package io.github.edtechdevelopment.identity.presentation.auth;

import com.jayway.jsonpath.JsonPath;
import io.github.edtechdevelopment.identity.application.port.out.security.RefreshTokenHasher;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityRefreshTokensRecord;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityUserEmailsRecord;
import io.github.edtechdevelopment.notifications.infrastructure.persistence.data.generated.tables.records.NotificationEmailDeliveriesRecord;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.IdentityRefreshTokens.IDENTITY_REFRESH_TOKENS;
import static io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.IdentityUserEmails.IDENTITY_USER_EMAILS;
import static io.github.edtechdevelopment.notifications.infrastructure.persistence.data.generated.tables.NotificationEmailDeliveries.NOTIFICATION_EMAIL_DELIVERIES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LoginFlowIntegrationTest {

    private static final String RAW_PASSWORD = "StrongPassword42!";

    private final MockMvc mockMvc;
    private final DSLContext dslContext;
    private final RefreshTokenHasher refreshTokenHasher;
    private final JwtDecoder jwtDecoder;

    @Autowired
    LoginFlowIntegrationTest(
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
    void rejectsPasswordOutsideTheAcceptedRegistrationAlphabet() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("login-validation@example.test", "Password 42")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("Pattern"));
    }

    @Test
    void logsInActiveUserAndCreatesIndependentRefreshFamily() throws Exception {
        String email = "login-flow-" + UUID.randomUUID() + "@example.test";
        registerUser(email);
        UUID userId = currentUserId(email);
        confirmEmail(email);

        IdentityRefreshTokensRecord confirmationRefresh = dslContext
                .selectFrom(IDENTITY_REFRESH_TOKENS)
                .where(IDENTITY_REFRESH_TOKENS.USER_ID.eq(userId))
                .fetchSingle();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email.toUpperCase(), RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").isNumber())
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        String accessToken = JsonPath.read(responseBody, "$.accessToken");
        String setCookie = loginResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        String rawRefreshToken = extractRefreshToken(setCookie);
        String loginRefreshHash = refreshTokenHasher.hash(rawRefreshToken);
        Jwt decodedAccessToken = jwtDecoder.decode(accessToken);

        List<IdentityRefreshTokensRecord> allRefreshTokens = dslContext
                .selectFrom(IDENTITY_REFRESH_TOKENS)
                .where(IDENTITY_REFRESH_TOKENS.USER_ID.eq(userId))
                .fetch();
        IdentityRefreshTokensRecord loginRefresh = allRefreshTokens.stream()
                .filter(record -> loginRefreshHash.equals(record.getTokenHash()))
                .findFirst()
                .orElseThrow();

        assertEquals(2, allRefreshTokens.size());
        assertNotEquals(confirmationRefresh.getFamilyId(), loginRefresh.getFamilyId());
        assertNotEquals(rawRefreshToken, loginRefresh.getTokenHash());
        assertEquals(userId.toString(), decodedAccessToken.getSubject());
        assertEquals(List.of("STUDENT"), decodedAccessToken.getClaimAsStringList("roles"));
        assertNotNull(setCookie);
        assertTrue(setCookie.contains("REFRESH_TOKEN="));
        assertTrue(setCookie.contains("Path=/api/v1/auth"));
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("SameSite=Lax"));
        assertFalse(setCookie.contains("Secure"));
    }

    @Test
    void returnsTheSamePublicErrorForUnknownEmailAndWrongPassword() throws Exception {
        String email = "login-invalid-" + UUID.randomUUID() + "@example.test";
        registerUser(email);
        confirmEmail(email);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, "WrongPassword42!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("unknown-" + UUID.randomUUID() + "@example.test", RAW_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void requiresEmailVerificationAfterCorrectPassword() throws Exception {
        String email = "login-pending-" + UUID.randomUUID() + "@example.test";
        registerUser(email);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, RAW_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"))
                .andExpect(jsonPath("$.message").value("Email verification is required"));
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

    private void confirmEmail(String email) throws Exception {
        NotificationEmailDeliveriesRecord deliveryRecord = dslContext
                .selectFrom(NOTIFICATION_EMAIL_DELIVERIES)
                .where(NOTIFICATION_EMAIL_DELIVERIES.RECIPIENT_EMAIL.eq(email))
                .fetchSingle();
        URI confirmationUri = URI.create(deliveryRecord.getConfirmationUrl());
        String rawVerificationToken = UriComponentsBuilder.fromUri(confirmationUri)
                .build()
                .getQueryParams()
                .getFirst("token");

        mockMvc.perform(post("/api/v1/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawVerificationToken + "\"}"))
                .andExpect(status().isOk());
    }

    private UUID currentUserId(String email) {
        IdentityUserEmailsRecord emailRecord = dslContext
                .selectFrom(IDENTITY_USER_EMAILS)
                .where(IDENTITY_USER_EMAILS.EMAIL.eq(email))
                .fetchSingle();
        return emailRecord.getUserId();
    }

    private static String loginJson(String email, String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);
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
}
