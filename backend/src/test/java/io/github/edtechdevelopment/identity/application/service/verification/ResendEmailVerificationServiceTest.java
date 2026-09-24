package io.github.edtechdevelopment.identity.application.service.verification;

import io.github.edtechdevelopment.identity.application.command.verification.ResendEmailVerificationCommand;
import io.github.edtechdevelopment.identity.application.exception.InvalidUseCaseInputException;
import io.github.edtechdevelopment.identity.application.port.out.TimeProvider;
import io.github.edtechdevelopment.identity.application.port.out.messaging.VerificationEmailSender;
import io.github.edtechdevelopment.identity.application.port.out.persistence.EmailVerificationRepository;
import io.github.edtechdevelopment.identity.application.port.out.persistence.UserRepository;
import io.github.edtechdevelopment.identity.application.port.out.security.VerificationTokenGenerator;
import io.github.edtechdevelopment.identity.application.port.out.security.VerificationTokenHasher;
import io.github.edtechdevelopment.identity.application.result.ResendVerificationResult;
import io.github.edtechdevelopment.identity.domain.user.model.Email;
import io.github.edtechdevelopment.identity.domain.user.model.PasswordHash;
import io.github.edtechdevelopment.identity.domain.user.model.User;
import io.github.edtechdevelopment.identity.domain.user.model.UserRole;
import io.github.edtechdevelopment.identity.domain.user.model.UserStatus;
import io.github.edtechdevelopment.identity.domain.verification.model.EmailVerification;
import io.github.edtechdevelopment.identity.domain.verification.model.VerificationPurpose;
import io.github.edtechdevelopment.identity.domain.verification.model.VerificationTokenHash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ResendEmailVerificationServiceTest {

    private static final UUID USER_ID = UUID.fromString("7714c947-8c40-489f-a5ca-c81857362a34");
    private static final Email CURRENT_EMAIL = new Email("anna@example.com");
    private static final Email PENDING_EMAIL = new Email("new.anna@example.com");
    private static final String RAW_TOKEN = "new-raw-verification-token";
    private static final VerificationTokenHash TOKEN_HASH = new VerificationTokenHash("a".repeat(64));
    private static final Instant CREATED_AT = Instant.parse("2026-09-24T10:00:00Z");
    private static final Instant REQUESTED_AT = Instant.parse("2026-09-24T11:00:00Z");
    private static final Duration TOKEN_TTL = Duration.ofMinutes(5);
    private static final Instant EXPIRES_AT = REQUESTED_AT.plus(TOKEN_TTL);

    private UserRepository userRepository;
    private EmailVerificationRepository emailVerificationRepository;
    private VerificationTokenGenerator verificationTokenGenerator;
    private VerificationTokenHasher verificationTokenHasher;
    private VerificationEmailSender verificationEmailSender;
    private TimeProvider timeProvider;
    private ResendEmailVerificationService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        emailVerificationRepository = mock(EmailVerificationRepository.class);
        verificationTokenGenerator = mock(VerificationTokenGenerator.class);
        verificationTokenHasher = mock(VerificationTokenHasher.class);
        verificationEmailSender = mock(VerificationEmailSender.class);
        timeProvider = mock(TimeProvider.class);
        service = new ResendEmailVerificationService(
                userRepository,
                emailVerificationRepository,
                verificationTokenGenerator,
                verificationTokenHasher,
                verificationEmailSender,
                timeProvider,
                TOKEN_TTL
        );
        when(timeProvider.now()).thenReturn(REQUESTED_AT);
    }

    @Test
    void resendsRegistrationVerificationForPendingAccountCurrentEmail() {
        User pendingUser = user(CURRENT_EMAIL, null, UserStatus.PENDING_EMAIL_VERIFICATION);
        stubUserLookup(CURRENT_EMAIL, pendingUser, pendingUser);
        stubTokenCreation();

        ResendVerificationResult result = service.resendEmailVerification(
                new ResendEmailVerificationCommand("  Anna@Example.com  ")
        );

        assertEquals(CURRENT_EMAIL.value(), result.email());
        assertEquals(EXPIRES_AT, result.verificationExpiresAt());
        assertNewVerification(CURRENT_EMAIL, VerificationPurpose.REGISTRATION);
    }

    @Test
    void resendsEmailChangeVerificationForActiveAccountPendingEmail() {
        User activeUser = user(CURRENT_EMAIL, PENDING_EMAIL, UserStatus.ACTIVE);
        stubUserLookup(PENDING_EMAIL, activeUser, activeUser);
        stubTokenCreation();

        service.resendEmailVerification(new ResendEmailVerificationCommand(PENDING_EMAIL.value()));

        assertNewVerification(PENDING_EMAIL, VerificationPurpose.EMAIL_CHANGE);
    }

    @Test
    void unknownEmailReturnsTheSameNeutralResultWithoutCreatingVerification() {
        Email unknownEmail = new Email("unknown@example.com");
        when(userRepository.findByCurrentOrPendingEmail(unknownEmail)).thenReturn(Optional.empty());

        ResendVerificationResult result = service.resendEmailVerification(
                new ResendEmailVerificationCommand(unknownEmail.value())
        );

        assertEquals(unknownEmail.value(), result.email());
        assertEquals(EXPIRES_AT, result.verificationExpiresAt());
        verifyNoInteractions(
                emailVerificationRepository,
                verificationTokenGenerator,
                verificationTokenHasher,
                verificationEmailSender
        );
        verify(userRepository, never()).findByIdForUpdate(USER_ID);
    }

    @Test
    void activeCurrentEmailDoesNotCreateAnotherVerification() {
        User activeUser = user(CURRENT_EMAIL, null, UserStatus.ACTIVE);
        stubUserLookup(CURRENT_EMAIL, activeUser, activeUser);

        service.resendEmailVerification(new ResendEmailVerificationCommand(CURRENT_EMAIL.value()));

        verifyNoInteractions(
                emailVerificationRepository,
                verificationTokenGenerator,
                verificationTokenHasher,
                verificationEmailSender
        );
    }

    @Test
    void rechecksPurposeAfterLockingUser() {
        User initiallyPending = user(CURRENT_EMAIL, null, UserStatus.PENDING_EMAIL_VERIFICATION);
        User alreadyActivated = user(CURRENT_EMAIL, null, UserStatus.ACTIVE);
        stubUserLookup(CURRENT_EMAIL, initiallyPending, alreadyActivated);

        service.resendEmailVerification(new ResendEmailVerificationCommand(CURRENT_EMAIL.value()));

        InOrder readOrder = inOrder(userRepository);
        readOrder.verify(userRepository).findByCurrentOrPendingEmail(CURRENT_EMAIL);
        readOrder.verify(userRepository).findByIdForUpdate(USER_ID);
        verifyNoInteractions(
                emailVerificationRepository,
                verificationTokenGenerator,
                verificationTokenHasher,
                verificationEmailSender
        );
    }

    @Test
    void rejectsMalformedEmailBeforeRepositoryLookup() {
        assertThrows(
                InvalidUseCaseInputException.class,
                () -> service.resendEmailVerification(new ResendEmailVerificationCommand("not-an-email"))
        );

        verifyNoInteractions(
                userRepository,
                emailVerificationRepository,
                verificationTokenGenerator,
                verificationTokenHasher,
                verificationEmailSender,
                timeProvider
        );
    }

    private void stubUserLookup(Email requestedEmail, User initialUser, User lockedUser) {
        when(userRepository.findByCurrentOrPendingEmail(requestedEmail)).thenReturn(Optional.of(initialUser));
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(lockedUser));
    }

    private void stubTokenCreation() {
        when(verificationTokenGenerator.generate()).thenReturn(RAW_TOKEN);
        when(verificationTokenHasher.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
    }

    private void assertNewVerification(Email targetEmail, VerificationPurpose purpose) {
        InOrder writeOrder = inOrder(emailVerificationRepository, verificationEmailSender);
        writeOrder.verify(emailVerificationRepository).invalidateActiveForUser(USER_ID, purpose, REQUESTED_AT);

        ArgumentCaptor<EmailVerification> verificationCaptor = ArgumentCaptor.forClass(EmailVerification.class);
        writeOrder.verify(emailVerificationRepository).save(verificationCaptor.capture());
        writeOrder.verify(verificationEmailSender).sendVerificationEmail(
                targetEmail,
                RAW_TOKEN,
                purpose,
                EXPIRES_AT
        );

        EmailVerification verification = verificationCaptor.getValue();
        assertEquals(USER_ID, verification.userId());
        assertEquals(targetEmail, verification.targetEmail());
        assertEquals(TOKEN_HASH, verification.tokenHash());
        assertEquals(purpose, verification.purpose());
        assertEquals(REQUESTED_AT, verification.createdAt());
        assertEquals(EXPIRES_AT, verification.expiresAt());
    }

    private static User user(Email currentEmail, Email pendingEmail, UserStatus status) {
        Instant verifiedAt = status == UserStatus.PENDING_EMAIL_VERIFICATION ? null : CREATED_AT;
        return User.reconstitute(
                USER_ID,
                currentEmail,
                pendingEmail,
                new PasswordHash("stored-password-hash"),
                "Anna",
                "Petrova",
                Set.of(UserRole.STUDENT),
                status,
                verifiedAt,
                CREATED_AT,
                CREATED_AT
        );
    }
}
