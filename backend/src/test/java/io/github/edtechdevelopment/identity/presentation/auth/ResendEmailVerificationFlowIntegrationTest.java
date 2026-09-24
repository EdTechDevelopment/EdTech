package io.github.edtechdevelopment.identity.presentation.auth;

import io.github.edtechdevelopment.identity.application.port.out.TimeProvider;
import io.github.edtechdevelopment.identity.application.port.out.persistence.UserRepository;
import io.github.edtechdevelopment.identity.application.port.out.security.VerificationTokenHasher;
import io.github.edtechdevelopment.identity.domain.user.model.Email;
import io.github.edtechdevelopment.identity.domain.user.model.User;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityEmailVerificationsRecord;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityUserEmailsRecord;
import io.github.edtechdevelopment.notifications.infrastructure.persistence.data.generated.tables.records.NotificationEmailDeliveriesRecord;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
import static io.github.edtechdevelopment.notifications.infrastructure.persistence.data.generated.tables.NotificationEmailDeliveries.NOTIFICATION_EMAIL_DELIVERIES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ResendEmailVerificationFlowIntegrationTest {

    private static final String RAW_PASSWORD = "StrongPassword42!";

    private final MockMvc mockMvc;
    private final DSLContext dslContext;
    private final UserRepository userRepository;
    private final VerificationTokenHasher verificationTokenHasher;
    private final TimeProvider timeProvider;

    @Autowired
    ResendEmailVerificationFlowIntegrationTest(
            MockMvc mockMvc,
            DSLContext dslContext,
            UserRepository userRepository,
            VerificationTokenHasher verificationTokenHasher,
            TimeProvider timeProvider
    ) {
        this.mockMvc = mockMvc;
        this.dslContext = dslContext;
        this.userRepository = userRepository;
        this.verificationTokenHasher = verificationTokenHasher;
        this.timeProvider = timeProvider;
    }

    @Test
    void resendsRegistrationVerificationAndOnlyTheNewestLinkCanBeConfirmed() throws Exception {
        String email = uniqueEmail("resend-registration");
        registerUser(email);
        NotificationEmailDeliveriesRecord originalDelivery = singleDelivery(email);
        String originalToken = rawToken(originalDelivery);

        performResend(email);

        NotificationEmailDeliveriesRecord resentDelivery = deliveryExcluding(email, originalDelivery.getId());
        String resentToken = rawToken(resentDelivery);
        IdentityEmailVerificationsRecord originalVerification = verificationByRawToken(originalToken);
        IdentityEmailVerificationsRecord resentVerification = verificationByRawToken(resentToken);

        assertEquals("REGISTRATION", resentDelivery.getPurpose());
        assertNotNull(originalVerification.getInvalidatedAt());
        assertNull(resentVerification.getInvalidatedAt());
        assertNull(resentVerification.getConsumedAt());

        confirmInvalidToken(originalToken);
        confirmValidToken(resentToken);
    }

    @Test
    void resendsEmailChangeVerificationAndConfirmsPendingEmail() throws Exception {
        String currentEmail = uniqueEmail("resend-current");
        String pendingEmail = uniqueEmail("resend-pending");
        registerUser(currentEmail);
        confirmValidToken(rawToken(singleDelivery(currentEmail)));
        requestEmailChange(currentEmail, pendingEmail);

        performResend(pendingEmail);
        NotificationEmailDeliveriesRecord firstDelivery = singleDelivery(pendingEmail);
        String firstToken = rawToken(firstDelivery);

        performResend(pendingEmail);
        NotificationEmailDeliveriesRecord secondDelivery = deliveryExcluding(pendingEmail, firstDelivery.getId());
        String secondToken = rawToken(secondDelivery);

        assertEquals("EMAIL_CHANGE", firstDelivery.getPurpose());
        assertEquals("EMAIL_CHANGE", secondDelivery.getPurpose());
        assertNotNull(verificationByRawToken(firstToken).getInvalidatedAt());
        assertNull(verificationByRawToken(secondToken).getInvalidatedAt());

        confirmInvalidToken(firstToken);
        confirmValidToken(secondToken);

        List<IdentityUserEmailsRecord> emailRecords = dslContext
                .selectFrom(IDENTITY_USER_EMAILS)
                .where(IDENTITY_USER_EMAILS.EMAIL.in(currentEmail, pendingEmail))
                .fetch();
        assertEquals(1, emailRecords.size());
        assertEquals(pendingEmail, emailRecords.getFirst().getEmail());
        assertEquals("CURRENT", emailRecords.getFirst().getKind());
    }

    @Test
    void unknownOrAlreadyConfirmedCurrentEmailReturnsNeutralAcceptedResponseWithoutNewDelivery() throws Exception {
        String unknownEmail = uniqueEmail("resend-unknown");
        performResend(unknownEmail);
        assertEquals(0, deliveryCount(unknownEmail));

        String activeEmail = uniqueEmail("resend-active");
        registerUser(activeEmail);
        confirmValidToken(rawToken(singleDelivery(activeEmail)));
        int deliveriesBeforeResend = deliveryCount(activeEmail);

        performResend(activeEmail);

        assertEquals(deliveriesBeforeResend, deliveryCount(activeEmail));
    }

    @Test
    void malformedEmailIsRejectedByHttpValidation() throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-verification/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
    }

    private void performResend(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-verification/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));
    }

    private void registerUser(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s",
                                  "firstName": "Resend",
                                  "lastName": "User",
                                  "roles": ["STUDENT"]
                                }
                                """.formatted(email, RAW_PASSWORD)))
                .andExpect(status().isAccepted());
    }

    private void requestEmailChange(String currentEmail, String pendingEmail) {
        User user = userRepository.findByEmail(new Email(currentEmail)).orElseThrow();
        user.requestEmailChange(new Email(pendingEmail), timeProvider.now());
        userRepository.save(user);
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

    private NotificationEmailDeliveriesRecord singleDelivery(String email) {
        return dslContext
                .selectFrom(NOTIFICATION_EMAIL_DELIVERIES)
                .where(NOTIFICATION_EMAIL_DELIVERIES.RECIPIENT_EMAIL.eq(email))
                .fetchSingle();
    }

    private NotificationEmailDeliveriesRecord deliveryExcluding(String email, UUID excludedId) {
        return dslContext
                .selectFrom(NOTIFICATION_EMAIL_DELIVERIES)
                .where(NOTIFICATION_EMAIL_DELIVERIES.RECIPIENT_EMAIL.eq(email))
                .and(NOTIFICATION_EMAIL_DELIVERIES.ID.ne(excludedId))
                .fetchSingle();
    }

    private int deliveryCount(String email) {
        return dslContext.fetchCount(
                NOTIFICATION_EMAIL_DELIVERIES,
                NOTIFICATION_EMAIL_DELIVERIES.RECIPIENT_EMAIL.eq(email)
        );
    }

    private IdentityEmailVerificationsRecord verificationByRawToken(String rawToken) {
        String tokenHash = verificationTokenHasher.hash(rawToken).value();
        return dslContext
                .selectFrom(IDENTITY_EMAIL_VERIFICATIONS)
                .where(IDENTITY_EMAIL_VERIFICATIONS.TOKEN_HASH.eq(tokenHash))
                .fetchSingle();
    }

    private static String rawToken(NotificationEmailDeliveriesRecord delivery) {
        URI confirmationUri = URI.create(delivery.getConfirmationUrl());
        return UriComponentsBuilder.fromUri(confirmationUri)
                .build()
                .getQueryParams()
                .getFirst("token");
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.test";
    }
}
