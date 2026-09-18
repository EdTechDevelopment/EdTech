package io.github.edtechdevelopment.identity.presentation.auth;

import com.jayway.jsonpath.JsonPath;
import io.github.edtechdevelopment.identity.application.port.out.security.PasswordHasher;
import io.github.edtechdevelopment.identity.application.port.out.security.VerificationTokenHasher;
import io.github.edtechdevelopment.identity.domain.user.model.PasswordHash;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityEmailVerificationsRecord;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityUserEmailsRecord;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityUsersRecord;
import io.github.edtechdevelopment.notifications.infrastructure.persistence.data.generated.tables.records.NotificationEmailDeliveriesRecord;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.IdentityEmailVerifications.IDENTITY_EMAIL_VERIFICATIONS;
import static io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.IdentityUserEmails.IDENTITY_USER_EMAILS;
import static io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.IdentityUserRoles.IDENTITY_USER_ROLES;
import static io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.IdentityUsers.IDENTITY_USERS;
import static io.github.edtechdevelopment.notifications.infrastructure.persistence.data.generated.tables.NotificationEmailDeliveries.NOTIFICATION_EMAIL_DELIVERIES;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
class RegistrationFlowIntegrationTest {

    private static final String RAW_PASSWORD = "StrongPassword42!";
    private static final String TEST_EMAIL_PREFIX = "registration-flow-";
    private static final String TEST_EMAIL_DOMAIN = "@example.test";

    private final MockMvc mockMvc;
    private final DSLContext dslContext;
    private final PasswordHasher passwordHasher;
    private final VerificationTokenHasher verificationTokenHasher;

    private String testEmail;
    private UUID createdUserId;

    @Autowired
    RegistrationFlowIntegrationTest(
            MockMvc mockMvc,
            DSLContext dslContext,
            PasswordHasher passwordHasher,
            VerificationTokenHasher verificationTokenHasher
    ) {
        this.mockMvc = mockMvc;
        this.dslContext = dslContext;
        this.passwordHasher = passwordHasher;
        this.verificationTokenHasher = verificationTokenHasher;
    }

    @BeforeEach
    void createClearlyMarkedTestEmail() {
        testEmail = TEST_EMAIL_PREFIX + UUID.randomUUID() + TEST_EMAIL_DOMAIN;
    }

    @Test
    void registersUserThroughHttpAndPersistsTheWholeRegistrationFlow() throws Exception {
        String requestEmail = testEmail.toUpperCase();

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegistrationJson(requestEmail)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.email").value(testEmail))
                .andExpect(jsonPath("$.verificationExpiresAt").isNotEmpty())
                .andReturn();

        IdentityUserEmailsRecord emailRecord = requireCurrentEmailRecord();
        createdUserId = emailRecord.getUserId();

        IdentityUsersRecord userRecord = dslContext
                .selectFrom(IDENTITY_USERS)
                .where(IDENTITY_USERS.ID.eq(createdUserId))
                .fetchSingle();

        Set<String> storedRoles = dslContext
                .select(IDENTITY_USER_ROLES.ROLE)
                .from(IDENTITY_USER_ROLES)
                .where(IDENTITY_USER_ROLES.USER_ID.eq(createdUserId))
                .fetchSet(IDENTITY_USER_ROLES.ROLE);

        IdentityEmailVerificationsRecord verificationRecord = dslContext
                .selectFrom(IDENTITY_EMAIL_VERIFICATIONS)
                .where(IDENTITY_EMAIL_VERIFICATIONS.USER_ID.eq(createdUserId))
                .fetchSingle();

        NotificationEmailDeliveriesRecord deliveryRecord = dslContext
                .selectFrom(NOTIFICATION_EMAIL_DELIVERIES)
                .where(NOTIFICATION_EMAIL_DELIVERIES.RECIPIENT_EMAIL.eq(testEmail))
                .fetchSingle();

        URI confirmationUri = URI.create(deliveryRecord.getConfirmationUrl());
        String rawVerificationToken = UriComponentsBuilder.fromUri(confirmationUri)
                .build()
                .getQueryParams()
                .getFirst("token");
        String responseBody = mvcResult.getResponse().getContentAsString();
        Instant responseExpiration = extractInstant(responseBody, "verificationExpiresAt");

        assertAll(
                () -> assertEquals(testEmail, emailRecord.getEmail()),
                () -> assertEquals("CURRENT", emailRecord.getKind()),
                () -> assertEquals("Test", userRecord.getFirstName()),
                () -> assertEquals("User", userRecord.getLastName()),
                () -> assertEquals("PENDING_EMAIL_VERIFICATION", userRecord.getStatus()),
                () -> assertNull(userRecord.getEmailVerifiedAt()),
                () -> assertNotEquals(RAW_PASSWORD, userRecord.getPasswordHash()),
                () -> assertTrue(passwordHasher.matches(
                        RAW_PASSWORD,
                        new PasswordHash(userRecord.getPasswordHash())
                )),
                () -> assertEquals(Set.of("STUDENT", "TEACHER"), storedRoles),
                () -> assertEquals(testEmail, verificationRecord.getTargetEmail()),
                () -> assertEquals("REGISTRATION", verificationRecord.getPurpose()),
                () -> assertTrue(verificationRecord.getTokenHash().matches("[0-9a-f]{64}")),
                () -> assertNull(verificationRecord.getConsumedAt()),
                () -> assertNull(verificationRecord.getInvalidatedAt()),
                () -> assertNotNull(rawVerificationToken),
                () -> assertFalse(rawVerificationToken.isBlank()),
                () -> assertNotEquals(rawVerificationToken, verificationRecord.getTokenHash()),
                () -> assertEquals(
                        verificationRecord.getTokenHash(),
                        verificationTokenHasher.hash(rawVerificationToken).value()
                ),
                () -> assertEquals("http", confirmationUri.getScheme()),
                () -> assertEquals("frontend.example", confirmationUri.getHost()),
                () -> assertEquals(3000, confirmationUri.getPort()),
                () -> assertEquals("/verify-email", confirmationUri.getPath()),
                () -> assertEquals("REGISTRATION", deliveryRecord.getPurpose()),
                () -> assertEquals("PENDING", deliveryRecord.getStatus()),
                () -> assertNull(deliveryRecord.getSentAt()),
                () -> assertEquals(
                        verificationRecord.getExpiresAt().toInstant(),
                        deliveryRecord.getExpiresAt().toInstant()
                ),
                () -> assertEquals(verificationRecord.getExpiresAt().toInstant(), responseExpiration),
                () -> assertEquals(
                        Duration.ofMinutes(5),
                        Duration.between(
                                verificationRecord.getCreatedAt().toInstant(),
                                verificationRecord.getExpiresAt().toInstant()
                        )
                )
        );
    }

    @Test
    void returnsValidationErrorWithoutCreatingDatabaseRecords() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegistrationJson(testEmail).replace(RAW_PASSWORD, "Password 42")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        assertNoTestRecordsRemain();
    }

    @Test
    void returnsConflictWhenEmailIsAlreadyRegistered() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegistrationJson(testEmail)))
                .andExpect(status().isAccepted());

        createdUserId = requireCurrentEmailRecord().getUserId();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegistrationJson(testEmail.toUpperCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("Email is already registered"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        assertEquals(1, countCurrentEmailRecords());
        assertEquals(1, countDeliveryRecords());
    }

    @AfterTransaction
    void testTransactionRemovedAllMarkedTestData() {
        assertNoTestRecordsRemain();
        if (createdUserId != null) {
            assertEquals(0, dslContext.fetchCount(
                    dslContext.selectFrom(IDENTITY_USERS).where(IDENTITY_USERS.ID.eq(createdUserId))
            ));
        }
    }

    private IdentityUserEmailsRecord requireCurrentEmailRecord() {
        return dslContext
                .selectFrom(IDENTITY_USER_EMAILS)
                .where(IDENTITY_USER_EMAILS.EMAIL.eq(testEmail))
                .and(IDENTITY_USER_EMAILS.KIND.eq("CURRENT"))
                .fetchSingle();
    }

    private void assertNoTestRecordsRemain() {
        assertAll(
                () -> assertEquals(0, countCurrentEmailRecords()),
                () -> assertEquals(0, dslContext.fetchCount(
                        dslContext.selectFrom(IDENTITY_EMAIL_VERIFICATIONS)
                                .where(IDENTITY_EMAIL_VERIFICATIONS.TARGET_EMAIL.eq(testEmail))
                )),
                () -> assertEquals(0, countDeliveryRecords())
        );
    }

    private int countCurrentEmailRecords() {
        return dslContext.fetchCount(
                dslContext.selectFrom(IDENTITY_USER_EMAILS)
                        .where(IDENTITY_USER_EMAILS.EMAIL.eq(testEmail))
        );
    }

    private int countDeliveryRecords() {
        return dslContext.fetchCount(
                dslContext.selectFrom(NOTIFICATION_EMAIL_DELIVERIES)
                        .where(NOTIFICATION_EMAIL_DELIVERIES.RECIPIENT_EMAIL.eq(testEmail))
        );
    }

    private static String validRegistrationJson(String email) {
        return """
                {
                  "email": "%s",
                  "password": "%s",
                  "firstName": "  Test  ",
                  "lastName": "  User  ",
                  "roles": ["STUDENT", "TEACHER"]
                }
                """.formatted(email, RAW_PASSWORD);
    }

    private static Instant extractInstant(String json, String fieldName) {
        return Instant.parse(JsonPath.read(json, "$." + fieldName));
    }
}
