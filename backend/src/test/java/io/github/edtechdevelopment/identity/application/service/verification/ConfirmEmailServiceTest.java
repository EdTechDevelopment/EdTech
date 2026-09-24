package io.github.edtechdevelopment.identity.application.service.verification;

import io.github.edtechdevelopment.identity.api.event.AccountEmailVerifiedEvent;
import io.github.edtechdevelopment.identity.api.event.UserAccountUpdatedEvent;
import io.github.edtechdevelopment.identity.api.event.UserActivatedEvent;
import io.github.edtechdevelopment.identity.api.model.AccountEmailVerificationPurpose;
import io.github.edtechdevelopment.identity.application.command.verification.ConfirmEmailCommand;
import io.github.edtechdevelopment.identity.application.exception.InvalidVerificationTokenException;
import io.github.edtechdevelopment.identity.application.mapper.IdentityApiMapper;
import io.github.edtechdevelopment.identity.application.model.IssuedAccessToken;
import io.github.edtechdevelopment.identity.application.model.IssuedRefreshToken;
import io.github.edtechdevelopment.identity.application.model.RefreshSession;
import io.github.edtechdevelopment.identity.application.model.RefreshTokenState;
import io.github.edtechdevelopment.identity.application.port.out.TimeProvider;
import io.github.edtechdevelopment.identity.application.port.out.messaging.IntegrationEventPublisher;
import io.github.edtechdevelopment.identity.application.port.out.persistence.EmailVerificationRepository;
import io.github.edtechdevelopment.identity.application.port.out.persistence.RefreshTokenRepository;
import io.github.edtechdevelopment.identity.application.port.out.persistence.UserRepository;
import io.github.edtechdevelopment.identity.application.port.out.security.AccessTokenIssuer;
import io.github.edtechdevelopment.identity.application.port.out.security.VerificationTokenHasher;
import io.github.edtechdevelopment.identity.application.result.AuthenticationResult;
import io.github.edtechdevelopment.identity.application.service.authentication.RefreshSessionFactory;
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

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConfirmEmailServiceTest {

    private static final UUID USER_ID = UUID.fromString("bb2d71bf-338b-43e4-b00d-e655557893b6");
    private static final UUID VERIFICATION_ID = UUID.fromString("34fd5c04-68d9-42b9-85ea-ce36d50a13a2");
    private static final Instant CREATED_AT = Instant.parse("2026-09-24T09:55:00Z");
    private static final Instant CONFIRMED_AT = Instant.parse("2026-09-24T10:00:00Z");
    private static final Instant VERIFICATION_EXPIRES_AT = Instant.parse("2026-09-24T10:05:00Z");
    private static final Instant ACCESS_EXPIRES_AT = Instant.parse("2026-09-24T10:15:00Z");
    private static final Instant REFRESH_EXPIRES_AT = Instant.parse("2026-10-24T10:00:00Z");
    private static final String RAW_VERIFICATION_TOKEN = "raw-verification-token";
    private static final String RAW_REFRESH_TOKEN = "raw-refresh-token";
    private static final String REFRESH_TOKEN_HASH = "b".repeat(64);
    private static final VerificationTokenHash VERIFICATION_TOKEN_HASH =
            new VerificationTokenHash("a".repeat(64));

    private UserRepository userRepository;
    private EmailVerificationRepository emailVerificationRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private VerificationTokenHasher verificationTokenHasher;
    private AccessTokenIssuer accessTokenIssuer;
    private RefreshSessionFactory refreshSessionFactory;
    private IntegrationEventPublisher integrationEventPublisher;
    private TimeProvider timeProvider;
    private ConfirmEmailService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        emailVerificationRepository = mock(EmailVerificationRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        verificationTokenHasher = mock(VerificationTokenHasher.class);
        accessTokenIssuer = mock(AccessTokenIssuer.class);
        refreshSessionFactory = mock(RefreshSessionFactory.class);
        integrationEventPublisher = mock(IntegrationEventPublisher.class);
        timeProvider = mock(TimeProvider.class);

        service = new ConfirmEmailService(
                userRepository,
                emailVerificationRepository,
                refreshTokenRepository,
                verificationTokenHasher,
                accessTokenIssuer,
                refreshSessionFactory,
                integrationEventPublisher,
                timeProvider,
                new IdentityApiMapper()
        );
    }

    @Test
    void confirmsRegistrationAndCreatesAuthenticatedRefreshSession() {
        User user = pendingUser();
        EmailVerification verification = registrationVerification();
        stubSuccessfulConfirmation(user, verification);

        AuthenticationResult result = service.confirmEmail(new ConfirmEmailCommand(RAW_VERIFICATION_TOKEN));

        InOrder lockingOrder = inOrder(emailVerificationRepository, userRepository);
        lockingOrder.verify(emailVerificationRepository).findActiveByTokenHash(
                VERIFICATION_TOKEN_HASH,
                CONFIRMED_AT
        );
        lockingOrder.verify(userRepository).findByIdForUpdate(USER_ID);
        lockingOrder.verify(emailVerificationRepository).findActiveByTokenHashForUpdate(
                VERIFICATION_TOKEN_HASH,
                CONFIRMED_AT
        );

        InOrder savingOrder = inOrder(userRepository, emailVerificationRepository, refreshTokenRepository);
        savingOrder.verify(userRepository).save(user);
        savingOrder.verify(emailVerificationRepository).save(verification);

        ArgumentCaptor<RefreshTokenState> refreshStateCaptor = ArgumentCaptor.forClass(RefreshTokenState.class);
        savingOrder.verify(refreshTokenRepository).save(refreshStateCaptor.capture());
        RefreshTokenState refreshState = refreshStateCaptor.getValue();

        assertEquals(UserStatus.ACTIVE, user.status());
        assertEquals(Optional.of(CONFIRMED_AT), user.emailVerifiedAt());
        assertEquals(Optional.of(CONFIRMED_AT), verification.consumedAt());
        assertEquals("signed.jwt.token", result.accessToken().value());
        assertEquals(RAW_REFRESH_TOKEN, result.refreshToken().value());
        assertNotNull(refreshState.id());
        assertNotNull(refreshState.familyId());
        assertEquals(USER_ID, refreshState.userId());
        assertEquals(REFRESH_TOKEN_HASH, refreshState.tokenHash());
        assertNotEquals(RAW_REFRESH_TOKEN, refreshState.tokenHash());
        assertEquals(REFRESH_EXPIRES_AT, refreshState.expiresAt());
        assertEquals(CONFIRMED_AT, refreshState.createdAt());
        assertFalse(refreshState.isRevoked());

        ArgumentCaptor<UserActivatedEvent> activatedEventCaptor =
                ArgumentCaptor.forClass(UserActivatedEvent.class);
        verify(integrationEventPublisher).publish(activatedEventCaptor.capture());
        assertEquals(USER_ID, activatedEventCaptor.getValue().userId());
        assertEquals("anna@example.com", activatedEventCaptor.getValue().email());

        ArgumentCaptor<AccountEmailVerifiedEvent> verifiedEventCaptor =
                ArgumentCaptor.forClass(AccountEmailVerifiedEvent.class);
        verify(integrationEventPublisher).publish(verifiedEventCaptor.capture());
        assertEquals(AccountEmailVerificationPurpose.REGISTRATION, verifiedEventCaptor.getValue().purpose());
    }

    @Test
    void confirmsPendingEmailWithTheSameUseCase() {
        User user = activeUserWithPendingEmail();
        EmailVerification verification = emailChangeVerification();
        stubSuccessfulConfirmation(user, verification);

        service.confirmEmail(new ConfirmEmailCommand(RAW_VERIFICATION_TOKEN));

        assertEquals(new Email("new@example.com"), user.email());
        assertTrue(user.pendingEmail().isEmpty());
        assertEquals(Optional.of(CONFIRMED_AT), verification.consumedAt());

        ArgumentCaptor<UserAccountUpdatedEvent> updatedEventCaptor =
                ArgumentCaptor.forClass(UserAccountUpdatedEvent.class);
        verify(integrationEventPublisher).publish(updatedEventCaptor.capture());
        assertTrue(updatedEventCaptor.getValue().changedFields().contains("email"));

        ArgumentCaptor<AccountEmailVerifiedEvent> verifiedEventCaptor =
                ArgumentCaptor.forClass(AccountEmailVerifiedEvent.class);
        verify(integrationEventPublisher).publish(verifiedEventCaptor.capture());
        assertEquals(AccountEmailVerificationPurpose.EMAIL_CHANGE, verifiedEventCaptor.getValue().purpose());
    }

    @Test
    void rejectsTokenThatBecameInactiveBeforeTheLockingRead() {
        User user = pendingUser();
        EmailVerification verification = registrationVerification();
        when(timeProvider.now()).thenReturn(CONFIRMED_AT);
        when(verificationTokenHasher.hash(RAW_VERIFICATION_TOKEN)).thenReturn(VERIFICATION_TOKEN_HASH);
        when(emailVerificationRepository.findActiveByTokenHash(VERIFICATION_TOKEN_HASH, CONFIRMED_AT))
                .thenReturn(Optional.of(verification));
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(emailVerificationRepository.findActiveByTokenHashForUpdate(VERIFICATION_TOKEN_HASH, CONFIRMED_AT))
                .thenReturn(Optional.empty());

        assertThrows(
                InvalidVerificationTokenException.class,
                () -> service.confirmEmail(new ConfirmEmailCommand(RAW_VERIFICATION_TOKEN))
        );

        verify(userRepository, never()).save(user);
        verify(emailVerificationRepository, never()).save(verification);
        verifyNoInteractions(
                refreshTokenRepository,
                accessTokenIssuer,
                refreshSessionFactory,
                integrationEventPublisher
        );
    }

    private void stubSuccessfulConfirmation(User user, EmailVerification verification) {
        when(timeProvider.now()).thenReturn(CONFIRMED_AT);
        when(verificationTokenHasher.hash(RAW_VERIFICATION_TOKEN)).thenReturn(VERIFICATION_TOKEN_HASH);
        when(emailVerificationRepository.findActiveByTokenHash(VERIFICATION_TOKEN_HASH, CONFIRMED_AT))
                .thenReturn(Optional.of(verification));
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(emailVerificationRepository.findActiveByTokenHashForUpdate(VERIFICATION_TOKEN_HASH, CONFIRMED_AT))
                .thenReturn(Optional.of(verification));
        when(accessTokenIssuer.issue(user, CONFIRMED_AT))
                .thenReturn(new IssuedAccessToken("signed.jwt.token", ACCESS_EXPIRES_AT));
        when(refreshSessionFactory.create(USER_ID, CONFIRMED_AT)).thenReturn(new RefreshSession(
                new IssuedRefreshToken(RAW_REFRESH_TOKEN, REFRESH_EXPIRES_AT),
                new RefreshTokenState(
                        UUID.randomUUID(),
                        USER_ID,
                        REFRESH_TOKEN_HASH,
                        UUID.randomUUID(),
                        REFRESH_EXPIRES_AT,
                        null,
                        CONFIRMED_AT
                )
        ));
    }

    private static User pendingUser() {
        return User.reconstitute(
                USER_ID,
                new Email("anna@example.com"),
                null,
                new PasswordHash("stored-password-hash"),
                "Anna",
                "Petrova",
                Set.of(UserRole.STUDENT),
                UserStatus.PENDING_EMAIL_VERIFICATION,
                null,
                CREATED_AT,
                CREATED_AT
        );
    }

    private static User activeUserWithPendingEmail() {
        return User.reconstitute(
                USER_ID,
                new Email("anna@example.com"),
                new Email("new@example.com"),
                new PasswordHash("stored-password-hash"),
                "Anna",
                "Petrova",
                Set.of(UserRole.STUDENT),
                UserStatus.ACTIVE,
                CREATED_AT,
                CREATED_AT,
                CREATED_AT
        );
    }

    private static EmailVerification registrationVerification() {
        return verification(new Email("anna@example.com"), VerificationPurpose.REGISTRATION);
    }

    private static EmailVerification emailChangeVerification() {
        return verification(new Email("new@example.com"), VerificationPurpose.EMAIL_CHANGE);
    }

    private static EmailVerification verification(Email email, VerificationPurpose purpose) {
        return EmailVerification.reconstitute(
                VERIFICATION_ID,
                USER_ID,
                email,
                VERIFICATION_TOKEN_HASH,
                purpose,
                CREATED_AT,
                VERIFICATION_EXPIRES_AT,
                null,
                null
        );
    }
}
