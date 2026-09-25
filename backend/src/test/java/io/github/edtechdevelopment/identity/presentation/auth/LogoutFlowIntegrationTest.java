package io.github.edtechdevelopment.identity.presentation.auth;

import io.github.edtechdevelopment.identity.application.port.out.security.RefreshTokenHasher;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityRefreshTokensRecord;
import io.github.edtechdevelopment.notifications.infrastructure.persistence.data.generated.tables.records.NotificationEmailDeliveriesRecord;
import jakarta.servlet.http.Cookie;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.IdentityRefreshTokens.IDENTITY_REFRESH_TOKENS;
import static io.github.edtechdevelopment.notifications.infrastructure.persistence.data.generated.tables.NotificationEmailDeliveries.NOTIFICATION_EMAIL_DELIVERIES;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LogoutFlowIntegrationTest {

    private static final String FRONTEND_ORIGIN = "http://frontend.example:3000";
    private static final String RAW_PASSWORD = "StrongPassword42!";

    private final MockMvc mockMvc;
    private final DSLContext dslContext;
    private final RefreshTokenHasher refreshTokenHasher;

    @Autowired
    LogoutFlowIntegrationTest(
            MockMvc mockMvc,
            DSLContext dslContext,
            RefreshTokenHasher refreshTokenHasher
    ) {
        this.mockMvc = mockMvc;
        this.dslContext = dslContext;
        this.refreshTokenHasher = refreshTokenHasher;
    }

    @Test
    void revokesOnlyThePresentedSessionFamilyAndRemainsIdempotent() throws Exception {
        String email = "logout-flow-" + UUID.randomUUID() + "@example.test";
        registerUser(email);
        String confirmedSessionToken = confirmEmailAndExtractRefreshToken(email);
        String secondSessionToken = loginAndExtractRefreshToken(email);
        IdentityRefreshTokensRecord confirmedSession = tokenByRawValue(confirmedSessionToken);
        IdentityRefreshTokensRecord secondSession = tokenByRawValue(secondSessionToken);
        assertNotEquals(confirmedSession.getFamilyId(), secondSession.getFamilyId());

        MvcResult logoutResult = performLogout(confirmedSessionToken)
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andReturn();

        assertClearedRefreshCookie(logoutResult.getResponse().getHeader(HttpHeaders.SET_COOKIE));
        assertFamilyRevoked(confirmedSession.getUserId(), confirmedSession.getFamilyId());
        assertFamilyActive(secondSession.getUserId(), secondSession.getFamilyId());

        performRefresh(confirmedSessionToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        MvcResult repeatedLogout = performLogout(confirmedSessionToken)
                .andExpect(status().isNoContent())
                .andReturn();
        assertClearedRefreshCookie(repeatedLogout.getResponse().getHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    void missingOrUnknownCookieStillCompletesLogoutAndClearsCookie() throws Exception {
        MvcResult missingCookieResult = mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN))
                .andExpect(status().isNoContent())
                .andReturn();
        assertClearedRefreshCookie(missingCookieResult.getResponse().getHeader(HttpHeaders.SET_COOKIE));

        MvcResult unknownCookieResult = performLogout("unknown-refresh-token")
                .andExpect(status().isNoContent())
                .andReturn();
        assertClearedRefreshCookie(unknownCookieResult.getResponse().getHeader(HttpHeaders.SET_COOKIE));
    }

    private ResultActions performLogout(String rawRefreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/logout")
                .header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
                .cookie(new Cookie("REFRESH_TOKEN", rawRefreshToken)));
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
                                  "firstName": "Logout",
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

    private String loginAndExtractRefreshToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return extractRefreshToken(result.getResponse().getHeader(HttpHeaders.SET_COOKIE));
    }

    private IdentityRefreshTokensRecord tokenByRawValue(String rawRefreshToken) {
        return dslContext
                .selectFrom(IDENTITY_REFRESH_TOKENS)
                .where(IDENTITY_REFRESH_TOKENS.TOKEN_HASH.eq(refreshTokenHasher.hash(rawRefreshToken)))
                .fetchSingle();
    }

    private void assertFamilyRevoked(UUID userId, UUID familyId) {
        List<IdentityRefreshTokensRecord> family = familyTokens(userId, familyId);
        assertFalse(family.isEmpty());
        assertTrue(family.stream().allMatch(token -> token.getRevokedAt() != null));
    }

    private void assertFamilyActive(UUID userId, UUID familyId) {
        List<IdentityRefreshTokensRecord> family = familyTokens(userId, familyId);
        assertFalse(family.isEmpty());
        assertTrue(family.stream().allMatch(token -> token.getRevokedAt() == null));
    }

    private List<IdentityRefreshTokensRecord> familyTokens(UUID userId, UUID familyId) {
        return dslContext
                .selectFrom(IDENTITY_REFRESH_TOKENS)
                .where(IDENTITY_REFRESH_TOKENS.USER_ID.eq(userId))
                .and(IDENTITY_REFRESH_TOKENS.FAMILY_ID.eq(familyId))
                .fetch();
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
