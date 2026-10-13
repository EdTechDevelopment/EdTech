package io.github.edtechdevelopment.identity.application.service.account;

import io.github.edtechdevelopment.identity.api.event.UserAccountUpdatedEvent;
import io.github.edtechdevelopment.identity.application.command.account.UpdateCurrentUserCommand;
import io.github.edtechdevelopment.identity.application.exception.AccountOperationNotAllowedException;
import io.github.edtechdevelopment.identity.application.exception.EmailAlreadyExistsException;
import io.github.edtechdevelopment.identity.application.mapper.IdentityApiMapper;
import io.github.edtechdevelopment.identity.application.mapper.UserResultMapper;
import io.github.edtechdevelopment.identity.application.port.out.TimeProvider;
import io.github.edtechdevelopment.identity.application.port.out.messaging.IntegrationEventPublisher;
import io.github.edtechdevelopment.identity.application.port.out.messaging.VerificationEmailSender;
import io.github.edtechdevelopment.identity.application.port.out.persistence.EmailVerificationRepository;
import io.github.edtechdevelopment.identity.application.port.out.persistence.UserRepository;
import io.github.edtechdevelopment.identity.application.port.out.security.VerificationTokenGenerator;
import io.github.edtechdevelopment.identity.application.port.out.security.VerificationTokenHasher;
import io.github.edtechdevelopment.identity.application.result.CurrentUserResult;
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
import java.time.LocalDate;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UpdateCurrentUserServiceTest {

    private static final UUID USER_ID = UUID.fromString("42f64de5-9ab4-4e53-95c6-c0de36577ec9");
    private static final Email CURRENT_EMAIL = new Email("anna@example.com");
    private static final Email NEW_EMAIL = new Email("new.anna@example.com");
    private static final Instant CREATED_AT = Instant.parse("2026-09-24T08:00:00Z");
    private static final Instant VERIFIED_AT = Instant.parse("2026-09-24T08:05:00Z");
    private static final Instant CHANGED_AT = Instant.parse("2026-09-24T12:00:00Z");
    private static final Duration VERIFICATION_TTL = Duration.ofMinutes(5);
    private static final String RAW_TOKEN = "new-email-verification-token";
    private static final VerificationTokenHash TOKEN_HASH = new VerificationTokenHash("b".repeat(64));

    private UserRepository userRepository;
    private EmailVerificationRepository emailVerificationRepository;
    private VerificationTokenGenerator verificationTokenGenerator;
    private VerificationTokenHasher verificationTokenHasher;
    private VerificationEmailSender verificationEmailSender;
    private IntegrationEventPublisher integrationEventPublisher;
    private TimeProvider timeProvider;
    private UpdateCurrentUserService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        emailVerificationRepository = mock(EmailVerificationRepository.class);
        verificationTokenGenerator = mock(VerificationTokenGenerator.class);
        verificationTokenHasher = mock(VerificationTokenHasher.class);
        verificationEmailSender = mock(VerificationEmailSender.class);
        integrationEventPublisher = mock(IntegrationEventPublisher.class);
        timeProvider = mock(TimeProvider.class);
        service = new UpdateCurrentUserService(
                userRepository,
                emailVerificationRepository,
                verificationTokenGenerator,
                verificationTokenHasher,
                verificationEmailSender,
                integrationEventPublisher,
                timeProvider,
                new UserResultMapper(),
                new IdentityApiMapper(),
                VERIFICATION_TTL
        );
        when(timeProvider.now()).thenReturn(CHANGED_AT);
    }

    @Test
    void updatesNamesWithoutStartingEmailVerification() {
        User user = activeUser(null);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));

        CurrentUserResult result = service.updateCurrentUser(
                new UpdateCurrentUserCommand(USER_ID, null, "  Anna-Maria  ", "  Sidorova  ")
        );

        assertEquals("Anna-Maria", result.firstName());
        assertEquals("Sidorova", result.lastName());
        assertEquals(CHANGED_AT, result.updatedAt());
        verify(userRepository).save(user);
        verify(integrationEventPublisher).publish(any(UserAccountUpdatedEvent.class));
        verifyNoInteractions(
                emailVerificationRepository,
                verificationTokenGenerator,
                verificationTokenHasher,
                verificationEmailSender
        );
    }

    @Test
    void requestsNewEmailAndCreatesEmailChangeVerification() {
        User user = activeUser(null);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail(NEW_EMAIL)).thenReturn(false);
        when(verificationTokenGenerator.generate()).thenReturn(RAW_TOKEN);
        when(verificationTokenHasher.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);

        CurrentUserResult result = service.updateCurrentUser(
                new UpdateCurrentUserCommand(USER_ID, NEW_EMAIL.value(), null, null)
        );

        assertEquals(CURRENT_EMAIL.value(), result.email());
        assertEquals(NEW_EMAIL.value(), result.pendingEmail());

        InOrder writeOrder = inOrder(userRepository, emailVerificationRepository, verificationEmailSender);
        writeOrder.verify(emailVerificationRepository).invalidateActiveForUser(
                USER_ID,
                VerificationPurpose.EMAIL_CHANGE,
                CHANGED_AT
        );
        writeOrder.verify(userRepository).save(user);

        ArgumentCaptor<EmailVerification> verificationCaptor = ArgumentCaptor.forClass(EmailVerification.class);
        writeOrder.verify(emailVerificationRepository).save(verificationCaptor.capture());
        writeOrder.verify(verificationEmailSender).sendVerificationEmail(
                NEW_EMAIL,
                RAW_TOKEN,
                VerificationPurpose.EMAIL_CHANGE,
                CHANGED_AT.plus(VERIFICATION_TTL)
        );

        EmailVerification verification = verificationCaptor.getValue();
        assertEquals(USER_ID, verification.userId());
        assertEquals(NEW_EMAIL, verification.targetEmail());
        assertEquals(TOKEN_HASH, verification.tokenHash());
        assertEquals(VerificationPurpose.EMAIL_CHANGE, verification.purpose());
        verify(integrationEventPublisher).publish(any(UserAccountUpdatedEvent.class));
    }

    @Test
    void sameCurrentOrPendingEmailDoesNotSendAnotherLetter() {
        User user = activeUser(NEW_EMAIL);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));

        service.updateCurrentUser(new UpdateCurrentUserCommand(USER_ID, CURRENT_EMAIL.value(), null, null));
        service.updateCurrentUser(new UpdateCurrentUserCommand(USER_ID, NEW_EMAIL.value(), null, null));

        verify(userRepository, never()).existsByEmail(any());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(
                emailVerificationRepository,
                verificationTokenGenerator,
                verificationTokenHasher,
                verificationEmailSender,
                integrationEventPublisher
        );
    }

    @Test
    void rejectsEmailOwnedByAnotherUser() {
        User user = activeUser(null);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail(NEW_EMAIL)).thenReturn(true);

        assertThrows(
                EmailAlreadyExistsException.class,
                () -> service.updateCurrentUser(
                        new UpdateCurrentUserCommand(USER_ID, NEW_EMAIL.value(), null, null)
                )
        );

        verify(userRepository, never()).save(any());
        verifyNoInteractions(
                emailVerificationRepository,
                verificationTokenGenerator,
                verificationTokenHasher,
                verificationEmailSender,
                integrationEventPublisher
        );
    }

    @Test
    void rejectsAccountThatIsNotActive() {
        User pendingUser = user(null, UserStatus.PENDING_EMAIL_VERIFICATION);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(pendingUser));

        assertThrows(
                AccountOperationNotAllowedException.class,
                () -> service.updateCurrentUser(
                        new UpdateCurrentUserCommand(USER_ID, null, "New name", null)
                )
        );

        verify(userRepository, never()).save(any());
    }

    private static User activeUser(Email pendingEmail) {
        return user(pendingEmail, UserStatus.ACTIVE);
    }

    private static User user(Email pendingEmail, UserStatus status) {
        return User.reconstitute(
                USER_ID,
                CURRENT_EMAIL,
                pendingEmail,
                new PasswordHash("stored-password-hash"),
                "Anna",
                "Petrova",
                LocalDate.of(2000, 1, 1),
                Set.of(UserRole.STUDENT),
                status,
                status == UserStatus.ACTIVE ? VERIFIED_AT : null,
                CREATED_AT,
                VERIFIED_AT
        );
    }
}
