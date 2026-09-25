package io.github.edtechdevelopment.identity.presentation.auth;

import com.jayway.jsonpath.JsonPath;
import io.github.edtechdevelopment.identity.application.port.out.security.RefreshTokenHasher;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityRefreshTokensRecord;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityUserEmailsRecord;
import io.github.edtechdevelopment.notifications.infrastructure.persistence.data.generated.tables.records.NotificationEmailDeliveriesRecord;
import jakarta.servlet.http.Cookie;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.IdentityRefreshTokens.IDENTITY_REFRESH_TOKENS;
import static io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.IdentityUserEmails.IDENTITY_USER_EMAILS;
import static io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.IdentityUsers.IDENTITY_USERS;
import static io.github.edtechdevelopment.notifications.infrastructure.persistence.data.generated.tables.NotificationEmailDeliveries.NOTIFICATION_EMAIL_DELIVERIES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RefreshTokenFlowIntegrationTest {

    private static final String FRONTEND_ORIGIN = "http://frontend.example:3000";
    private static final String RAW_PASSWORD = "StrongPassword42!";

    private final MockMvc mockMvc;
    private final DSLContext dslContext;
    private final RefreshTokenHasher refreshTokenHasher;
    private final JwtDecoder jwtDecoder;

    @Autowired
    RefreshTokenFlowIntegrationTest(
            MockMvc mockMvc,
            DSLContext dslContext,
            RefreshTokenHasher refreshTokenHasher,
            JwtDecoder jwtDecoder
    ) {
        this.mockMvc = mockMvc;
        this.dslContext = dslContext;
        this.refreshTokenHasher = refreshTokenHasher;
        this.jwtDecoder = jwtDecoder;
    }

    @Test
    void rotatesRefreshTokenAndRevokesFamilyWhenOldTokenIsReused() throws Exception {
        String email = "refresh-flow-" + UUID.randomUUID() + "@example.test";
        registerUser(email);
        UUID userId = currentUserId(email);
        String originalRawToken = confirmEmailAndExtractRefreshToken(email);
        String originalHash = refreshTokenHasher.hash(originalRawToken);
        IdentityRefreshTokensRecord originalBeforeRefresh = tokenByHash(originalHash);

        MvcResult refreshResult = performRefresh(originalRawToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();

        String responseBody = refreshResult.getResponse().getContentAsString();
        String accessToken = JsonPath.read(responseBody, "$.accessToken");
        String rotatedRawToken = extractRefreshToken(
                refreshResult.getResponse().getHeader(HttpHeaders.SET_COOKIE)
        );
        String rotatedHash = refreshTokenHasher.hash(rotatedRawToken);
        Jwt decodedAccessToken = jwtDecoder.decode(accessToken);
        IdentityRefreshTokensRecord originalAfterRefresh = tokenByHash(originalHash);
        IdentityRefreshTokensRecord rotatedToken = tokenByHash(rotatedHash);

        assertNotEquals(originalRawToken, rotatedRawToken);
        assertNotNull(originalAfterRefresh.getRevokedAt());
        assertEquals(originalBeforeRefresh.getFamilyId(), rotatedToken.getFamilyId());
        assertEquals(originalBeforeRefresh.getExpiresAt(), rotatedToken.getExpiresAt());
        assertNull(rotatedToken.getRevokedAt());
        assertEquals(userId.toString(), decodedAccessToken.getSubject());
        assertEquals(List.of("STUDENT"), decodedAccessToken.getClaimAsStringList("roles"));

        MvcResult reuseResult = performRefresh(originalRawToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
                .andExpect(jsonPath("$.message").value("Refresh token is invalid or expired"))
                .andReturn();

        assertClearedRefreshCookie(reuseResult.getResponse().getHeader(HttpHeaders.SET_COOKIE));
        assertNotNull(tokenByHash(rotatedHash).getRevokedAt());

        performRefresh(rotatedRawToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void rejectsMissingRefreshCookieAndReturnsCookieRemoval() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
                .andReturn();

        assertClearedRefreshCookie(result.getResponse().getHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    void suspendedAccountRevokesPresentedFamilyAndReturnsForbidden() throws Exception {
        String email = "refresh-suspended-" + UUID.randomUUID() + "@example.test";
        registerUser(email);
        UUID userId = currentUserId(email);
        String rawRefreshToken = confirmEmailAndExtractRefreshToken(email);
        String tokenHash = refreshTokenHasher.hash(rawRefreshToken);
        UUID familyId = tokenByHash(tokenHash).getFamilyId();
        dslContext
                .update(IDENTITY_USERS)
                .set(IDENTITY_USERS.STATUS, "SUSPENDED")
                .where(IDENTITY_USERS.ID.eq(userId))
                .execute();

        MvcResult result = performRefresh(rawRefreshToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Account access is not allowed"))
                .andReturn();

        assertClearedRefreshCookie(result.getResponse().getHeader(HttpHeaders.SET_COOKIE));
        List<IdentityRefreshTokensRecord> familyTokens = dslContext
                .selectFrom(IDENTITY_REFRESH_TOKENS)
                .where(IDENTITY_REFRESH_TOKENS.USER_ID.eq(userId))
                .and(IDENTITY_REFRESH_TOKENS.FAMILY_ID.eq(familyId))
                .fetch();
        assertEquals(1, familyTokens.size());
        assertNotNull(familyTokens.getFirst().getRevokedAt());
    }

    private ResultActions performRefresh(String rawRefreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
                .cookie(new Cookie("REFRESH_TOKEN", rawRefreshToken)));
    }

    private void registerUser(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s",
                                  "firstName": "Test",
                                  "lastName": "User",
                                  "roles": ["STUDENT"]
                                }
                                """.formatted(email, RAW_PASSWORD)))
                .andExpect(status().isAccepted());
    }

    private String confirmEmailAndExtractRefreshToken(String email) throws Exception {
        NotificationEmailDeliveriesRecord deliveryRecord = dslContext
                .selectFrom(NOTIFICATION_EMAIL_DELIVERIES)
                .where(NOTIFICATION_EMAIL_DELIVERIES.RECIPIENT_EMAIL.eq(email))
                .fetchSingle();
        URI confirmationUri = URI.create(deliveryRecord.getConfirmationUrl());
        String rawVerificationToken = UriComponentsBuilder.fromUri(confirmationUri)
                .build()
                .getQueryParams()
                .getFirst("token");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawVerificationToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return extractRefreshToken(result.getResponse().getHeader(HttpHeaders.SET_COOKIE));
    }

    private UUID currentUserId(String email) {
        IdentityUserEmailsRecord emailRecord = dslContext
                .selectFrom(IDENTITY_USER_EMAILS)
                .where(IDENTITY_USER_EMAILS.EMAIL.eq(email))
                .fetchSingle();
        return emailRecord.getUserId();
    }

    private IdentityRefreshTokensRecord tokenByHash(String tokenHash) {
        return dslContext
                .selectFrom(IDENTITY_REFRESH_TOKENS)
                .where(IDENTITY_REFRESH_TOKENS.TOKEN_HASH.eq(tokenHash))
                .fetchSingle();
    }

    private static String extractRefreshToken(String setCookie) {
        assertNotNull(setCookie);
        String cookiePrefix = "REFRESH_TOKEN=";
        int valueStart = setCookie.indexOf(cookiePrefix);
        if (valueStart < 0) {
            throw new IllegalStateException("Refresh cookie is missing");
        }
        valueStart += cookiePrefix.length();
        int valueEnd = setCookie.indexOf(';', valueStart);
        return valueEnd < 0
                ? setCookie.substring(valueStart)
                : setCookie.substring(valueStart, valueEnd);
    }

    private static void assertClearedRefreshCookie(String setCookie) {
        assertNotNull(setCookie);
        assertTrue(setCookie.startsWith("REFRESH_TOKEN="));
        assertTrue(setCookie.contains("Max-Age=0"));
        assertTrue(setCookie.contains("Path=/api/v1/auth"));
        assertTrue(setCookie.contains("HttpOnly"));
    }
}
