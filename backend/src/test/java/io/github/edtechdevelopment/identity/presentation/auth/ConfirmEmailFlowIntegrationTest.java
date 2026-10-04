package io.github.edtechdevelopment.identity.presentation.auth;

import com.jayway.jsonpath.JsonPath;
import io.github.edtechdevelopment.identity.application.port.out.security.RefreshTokenHasher;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityEmailVerificationsRecord;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityRefreshTokensRecord;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityUserEmailsRecord;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityUsersRecord;
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
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.IdentityEmailVerifications.IDENTITY_EMAIL_VERIFICATIONS;
import static io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.IdentityRefreshTokens.IDENTITY_REFRESH_TOKENS;
import static io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.IdentityUserEmails.IDENTITY_USER_EMAILS;
import static io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.IdentityUsers.IDENTITY_USERS;
import static io.github.edtechdevelopment.notifications.infrastructure.persistence.data.generated.tables.NotificationEmailDeliveries.NOTIFICATION_EMAIL_DELIVERIES;
import static org.junit.jupiter.api.Assertions.assertAll;
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
class ConfirmEmailFlowIntegrationTest {

    private final MockMvc mockMvc;
    private final DSLContext dslContext;
    private final RefreshTokenHasher refreshTokenHasher;
    private final JwtDecoder jwtDecoder;

    @Autowired
    ConfirmEmailFlowIntegrationTest(
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
    void rejectsTooShortVerificationTokenBeforeApplicationService() throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"too-short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("token"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("Size"));
    }

    @Test
    void confirmsRegistrationIssuesTokensAndRejectsTokenReuse() throws Exception {
        String email = "confirm-flow-" + UUID.randomUUID() + "@example.test";
        registerUser(email);

        IdentityUserEmailsRecord emailRecord = dslContext
                .selectFrom(IDENTITY_USER_EMAILS)
                .where(IDENTITY_USER_EMAILS.EMAIL.eq(email))
                .fetchSingle();
        UUID userId = emailRecord.getUserId();

        IdentityEmailVerificationsRecord verificationBeforeConfirmation = dslContext
                .selectFrom(IDENTITY_EMAIL_VERIFICATIONS)
                .where(IDENTITY_EMAIL_VERIFICATIONS.USER_ID.eq(userId))
                .fetchSingle();
        String rawVerificationToken = rawVerificationToken(email);

        MvcResult confirmationResult = mockMvc.perform(post("/api/v1/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawVerificationToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(900))
                .andReturn();

        String responseBody = confirmationResult.getResponse().getContentAsString();
        String accessToken = JsonPath.read(responseBody, "$.accessToken");
        String setCookie = confirmationResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        String rawRefreshToken = extractRefreshToken(setCookie);
        Jwt decodedAccessToken = jwtDecoder.decode(accessToken);

        IdentityUsersRecord userRecord = dslContext
                .selectFrom(IDENTITY_USERS)
                .where(IDENTITY_USERS.ID.eq(userId))
                .fetchSingle();
        IdentityEmailVerificationsRecord verificationAfterConfirmation = dslContext
                .selectFrom(IDENTITY_EMAIL_VERIFICATIONS)
                .where(IDENTITY_EMAIL_VERIFICATIONS.ID.eq(verificationBeforeConfirmation.getId()))
                .fetchSingle();
        List<IdentityRefreshTokensRecord> refreshTokenRecords = dslContext
                .selectFrom(IDENTITY_REFRESH_TOKENS)
                .where(IDENTITY_REFRESH_TOKENS.USER_ID.eq(userId))
                .fetch();
        IdentityRefreshTokensRecord refreshTokenRecord = refreshTokenRecords.getFirst();

        assertAll(
                () -> assertEquals("ACTIVE", userRecord.getStatus()),
                () -> assertNotNull(userRecord.getEmailVerifiedAt()),
                () -> assertNotNull(verificationAfterConfirmation.getConsumedAt()),
                () -> assertEquals(1, refreshTokenRecords.size()),
                () -> assertNotNull(refreshTokenRecord.getFamilyId()),
                () -> assertNotNull(refreshTokenRecord.getId()),
                () -> assertEquals(userId, refreshTokenRecord.getUserId()),
                () -> assertEquals(
                        refreshTokenHasher.hash(rawRefreshToken),
                        refreshTokenRecord.getTokenHash()
                ),
                () -> assertNotEquals(rawRefreshToken, refreshTokenRecord.getTokenHash()),
                () -> assertEquals(
                        Duration.ofDays(30),
                        Duration.between(
                                refreshTokenRecord.getCreatedAt().toInstant(),
                                refreshTokenRecord.getExpiresAt().toInstant()
                        )
                ),
                () -> assertEquals(userId.toString(), decodedAccessToken.getSubject()),
                () -> assertEquals(List.of("STUDENT"), decodedAccessToken.getClaimAsStringList("roles")),
                () -> assertNotNull(setCookie),
                () -> assertTrue(setCookie.contains("REFRESH_TOKEN=")),
                () -> assertTrue(setCookie.contains("Path=/api/v1/auth")),
                () -> assertTrue(setCookie.contains("Max-Age=2592000")),
                () -> assertTrue(setCookie.contains("HttpOnly")),
                () -> assertTrue(setCookie.contains("SameSite=Lax")),
                () -> assertFalse(setCookie.contains("Secure"))
        );

        mockMvc.perform(post("/api/v1/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawVerificationToken + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"))
                .andExpect(jsonPath("$.message").value("Verification token is invalid or expired"));

        assertEquals(
                1,
                dslContext.fetchCount(
                        IDENTITY_REFRESH_TOKENS,
                        IDENTITY_REFRESH_TOKENS.USER_ID.eq(userId)
                )
        );
    }

    private void registerUser(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "StrongPassword42!",
                                  "firstName": "Test",
                                  "lastName": "User",
                                  "birthDate": "2000-01-01",
                                  "roles": ["STUDENT"]
                                }
                                """.formatted(email)))
                .andExpect(status().isAccepted());
    }

    private String rawVerificationToken(String email) {
        NotificationEmailDeliveriesRecord deliveryRecord = dslContext
                .selectFrom(NOTIFICATION_EMAIL_DELIVERIES)
                .where(NOTIFICATION_EMAIL_DELIVERIES.RECIPIENT_EMAIL.eq(email))
                .fetchSingle();
        URI confirmationUri = URI.create(deliveryRecord.getConfirmationUrl());
        return UriComponentsBuilder.fromUri(confirmationUri)
                .build()
                .getQueryParams()
                .getFirst("token");
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
