package io.github.edtechdevelopment.identity.presentation.account;

import com.jayway.jsonpath.JsonPath;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityEmailVerificationsRecord;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.IdentityEmailVerifications.IDENTITY_EMAIL_VERIFICATIONS;
import static io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.IdentityUserEmails.IDENTITY_USER_EMAILS;
import static io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.IdentityUsers.IDENTITY_USERS;
import static io.github.edtechdevelopment.notifications.infrastructure.persistence.data.generated.tables.NotificationEmailDeliveries.NOTIFICATION_EMAIL_DELIVERIES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UpdateCurrentUserFlowIntegrationTest {

    private static final String RAW_PASSWORD = "StrongPassword42!";

    private final MockMvc mockMvc;
    private final DSLContext dslContext;

    @Autowired
    UpdateCurrentUserFlowIntegrationTest(MockMvc mockMvc, DSLContext dslContext) {
        this.mockMvc = mockMvc;
        this.dslContext = dslContext;
    }

    @Test
    void updatesNamesReplacesPendingEmailAndConfirmsOnlyTheNewestAddress() throws Exception {
        String currentEmail = uniqueEmail("update-current");
        String firstPendingEmail = uniqueEmail("update-first-pending");
        String secondPendingEmail = uniqueEmail("update-second-pending");
        String accessToken = registerAndConfirm(currentEmail);
        UUID userId = userIdByEmail(currentEmail);

        mockMvc.perform(patch("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "  Anna-Maria  ",
                                  "lastName": "  Sidorova  ",
                                  "email": "%s"
                                }
                                """.formatted(firstPendingEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value(currentEmail))
                .andExpect(jsonPath("$.pendingEmail").value(firstPendingEmail))
                .andExpect(jsonPath("$.firstName").value("Anna-Maria"))
                .andExpect(jsonPath("$.lastName").value("Sidorova"))
                .andExpect(jsonPath("$.roles[0]").value("STUDENT"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.emailVerifiedAt").isNotEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        String firstEmailChangeToken = rawVerificationToken(firstPendingEmail);
        IdentityEmailVerificationsRecord firstVerification = emailChangeVerification(userId, firstPendingEmail);

        mockMvc.perform(patch("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + secondPendingEmail + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(currentEmail))
                .andExpect(jsonPath("$.pendingEmail").value(secondPendingEmail));

        String secondEmailChangeToken = rawVerificationToken(secondPendingEmail);
        IdentityEmailVerificationsRecord invalidatedFirstVerification = dslContext
                .selectFrom(IDENTITY_EMAIL_VERIFICATIONS)
                .where(IDENTITY_EMAIL_VERIFICATIONS.ID.eq(firstVerification.getId()))
                .fetchSingle();
        assertNotNull(invalidatedFirstVerification.getInvalidatedAt());

        confirmInvalidToken(firstEmailChangeToken);
        confirmValidToken(secondEmailChangeToken);

        List<IdentityUserEmailsRecord> emailRecords = dslContext
                .selectFrom(IDENTITY_USER_EMAILS)
                .where(IDENTITY_USER_EMAILS.USER_ID.eq(userId))
                .fetch();
        IdentityUsersRecord userRecord = dslContext
                .selectFrom(IDENTITY_USERS)
                .where(IDENTITY_USERS.ID.eq(userId))
                .fetchSingle();

        assertEquals(1, emailRecords.size());
        assertEquals(secondPendingEmail, emailRecords.getFirst().getEmail());
        assertEquals("CURRENT", emailRecords.getFirst().getKind());
        assertEquals("Anna-Maria", userRecord.getFirstName());
        assertEquals("Sidorova", userRecord.getLastName());
    }

    @Test
    void rejectsEmailOwnedByAnotherUser() throws Exception {
        String firstEmail = uniqueEmail("update-owner");
        String occupiedEmail = uniqueEmail("update-occupied");
        String firstAccessToken = registerAndConfirm(firstEmail);
        registerAndConfirm(occupiedEmail);

        mockMvc.perform(patch("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(firstAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + occupiedEmail + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void requiresAuthenticationAndAtLeastOneValidField() throws Exception {
        mockMvc.perform(patch("/api/v1/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Anna\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        String email = uniqueEmail("update-validation");
        String accessToken = registerAndConfirm(email);

        mockMvc.perform(patch("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(patch("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private String registerAndConfirm(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s",
                                  "firstName": "Test",
                                  "lastName": "User",
                                  "birthDate": "2000-01-01",
                                  "roles": ["STUDENT"]
                                }
                                """.formatted(email, RAW_PASSWORD)))
                .andExpect(status().isAccepted());

        MvcResult confirmationResult = mockMvc.perform(post("/api/v1/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawVerificationToken(email) + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.read(confirmationResult.getResponse().getContentAsString(), "$.accessToken");
    }

    private void confirmValidToken(String rawToken) throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    private void confirmInvalidToken(String rawToken) throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawToken + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"));
    }

    private String rawVerificationToken(String email) {
        NotificationEmailDeliveriesRecord delivery = dslContext
                .selectFrom(NOTIFICATION_EMAIL_DELIVERIES)
                .where(NOTIFICATION_EMAIL_DELIVERIES.RECIPIENT_EMAIL.eq(email))
                .fetchSingle();
        URI confirmationUri = URI.create(delivery.getConfirmationUrl());
        return UriComponentsBuilder.fromUri(confirmationUri)
                .build()
                .getQueryParams()
                .getFirst("token");
    }

    private IdentityEmailVerificationsRecord emailChangeVerification(UUID userId, String email) {
        return dslContext
                .selectFrom(IDENTITY_EMAIL_VERIFICATIONS)
                .where(IDENTITY_EMAIL_VERIFICATIONS.USER_ID.eq(userId))
                .and(IDENTITY_EMAIL_VERIFICATIONS.TARGET_EMAIL.eq(email))
                .and(IDENTITY_EMAIL_VERIFICATIONS.PURPOSE.eq("EMAIL_CHANGE"))
                .fetchSingle();
    }

    private UUID userIdByEmail(String email) {
        return dslContext
                .select(IDENTITY_USER_EMAILS.USER_ID)
                .from(IDENTITY_USER_EMAILS)
                .where(IDENTITY_USER_EMAILS.EMAIL.eq(email))
                .fetchSingle(IDENTITY_USER_EMAILS.USER_ID);
    }

    private static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.test";
    }
}
