package io.github.edtechdevelopment.identity.application.service.authentication;

import io.github.edtechdevelopment.identity.application.command.authentication.LoginCommand;
import io.github.edtechdevelopment.identity.application.exception.AccountOperationNotAllowedException;
import io.github.edtechdevelopment.identity.application.exception.EmailVerificationRequiredException;
import io.github.edtechdevelopment.identity.application.exception.InvalidCredentialsException;
import io.github.edtechdevelopment.identity.application.model.IssuedAccessToken;
import io.github.edtechdevelopment.identity.application.model.IssuedRefreshToken;
import io.github.edtechdevelopment.identity.application.model.RefreshSession;
import io.github.edtechdevelopment.identity.application.model.RefreshTokenState;
import io.github.edtechdevelopment.identity.application.port.out.TimeProvider;
import io.github.edtechdevelopment.identity.application.port.out.persistence.RefreshTokenRepository;
import io.github.edtechdevelopment.identity.application.port.out.persistence.UserRepository;
import io.github.edtechdevelopment.identity.application.port.out.security.AccessTokenIssuer;
import io.github.edtechdevelopment.identity.application.port.out.security.PasswordHasher;
import io.github.edtechdevelopment.identity.application.result.AuthenticationResult;
import io.github.edtechdevelopment.identity.domain.user.model.Email;
import io.github.edtechdevelopment.identity.domain.user.model.PasswordHash;
import io.github.edtechdevelopment.identity.domain.user.model.User;
import io.github.edtechdevelopment.identity.domain.user.model.UserRole;
import io.github.edtechdevelopment.identity.domain.user.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LoginServiceTest {

    private static final UUID USER_ID = UUID.fromString("31bc2bca-c542-4518-ae63-5ae093beed5e");
    private static final Instant AUTHENTICATED_AT = Instant.parse("2026-09-24T12:00:00Z");
    private static final Instant ACCESS_EXPIRES_AT = Instant.parse("2026-09-24T12:15:00Z");
    private static final Instant REFRESH_EXPIRES_AT = Instant.parse("2026-10-24T12:00:00Z");
    private static final String EMAIL = "anna@example.com";
    private static final String RAW_PASSWORD = "StrongPassword42!";
    private static final PasswordHash STORED_PASSWORD_HASH = new PasswordHash("stored-password-hash");

    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private PasswordHasher passwordHasher;
    private AccessTokenIssuer accessTokenIssuer;
    private RefreshSessionFactory refreshSessionFactory;
    private TimeProvider timeProvider;
    private LoginService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        passwordHasher = mock(PasswordHasher.class);
        accessTokenIssuer = mock(AccessTokenIssuer.class);
        refreshSessionFactory = mock(RefreshSessionFactory.class);
        timeProvider = mock(TimeProvider.class);
        service = new LoginService(
                userRepository,
                refreshTokenRepository,
                passwordHasher,
                accessTokenIssuer,
                refreshSessionFactory,
                timeProvider
        );
    }

    @Test
    void authenticatesActiveUserAndPersistsNewRefreshFamily() {
        User user = userWithStatus(UserStatus.ACTIVE);
        RefreshSession refreshSession = refreshSession();
        when(userRepository.findByEmail(new Email(EMAIL))).thenReturn(Optional.of(user));
        when(passwordHasher.matches(RAW_PASSWORD, STORED_PASSWORD_HASH)).thenReturn(true);
        when(timeProvider.now()).thenReturn(AUTHENTICATED_AT);
        when(accessTokenIssuer.issue(user, AUTHENTICATED_AT))
                .thenReturn(new IssuedAccessToken("signed.jwt.token", ACCESS_EXPIRES_AT));
        when(refreshSessionFactory.create(USER_ID, AUTHENTICATED_AT)).thenReturn(refreshSession);

        AuthenticationResult result = service.login(new LoginCommand("  ANNA@EXAMPLE.COM  ", RAW_PASSWORD));

        assertEquals("signed.jwt.token", result.accessToken().value());
        assertEquals("raw-refresh-token", result.refreshToken().value());
        verify(refreshTokenRepository).save(refreshSession.state());
    }

    @Test
    void performsDummyPasswordCheckAndReturnsGenericErrorForUnknownEmail() {
        when(userRepository.findByEmail(new Email(EMAIL))).thenReturn(Optional.empty());

        assertThrows(
                InvalidCredentialsException.class,
                () -> service.login(new LoginCommand(EMAIL, RAW_PASSWORD))
        );

        ArgumentCaptor<PasswordHash> checkedHash = ArgumentCaptor.forClass(PasswordHash.class);
        verify(passwordHasher).matches(org.mockito.ArgumentMatchers.eq(RAW_PASSWORD), checkedHash.capture());
        assertNotEquals(STORED_PASSWORD_HASH, checkedHash.getValue());
        verifyNoInteractions(timeProvider, accessTokenIssuer, refreshSessionFactory, refreshTokenRepository);
    }

    @Test
    void returnsTheSameGenericErrorForWrongPassword() {
        User user = userWithStatus(UserStatus.ACTIVE);
        when(userRepository.findByEmail(new Email(EMAIL))).thenReturn(Optional.of(user));
        when(passwordHasher.matches(RAW_PASSWORD, STORED_PASSWORD_HASH)).thenReturn(false);

        InvalidCredentialsException exception = assertThrows(
                InvalidCredentialsException.class,
                () -> service.login(new LoginCommand(EMAIL, RAW_PASSWORD))
        );

        assertEquals("Invalid email or password", exception.getMessage());
        verifyNoInteractions(timeProvider, accessTokenIssuer, refreshSessionFactory, refreshTokenRepository);
    }

    @Test
    void revealsPendingVerificationOnlyAfterCorrectPassword() {
        User user = userWithStatus(UserStatus.PENDING_EMAIL_VERIFICATION);
        when(userRepository.findByEmail(new Email(EMAIL))).thenReturn(Optional.of(user));
        when(passwordHasher.matches(RAW_PASSWORD, STORED_PASSWORD_HASH)).thenReturn(true);

        assertThrows(
                EmailVerificationRequiredException.class,
                () -> service.login(new LoginCommand(EMAIL, RAW_PASSWORD))
        );

        verifyNoInteractions(timeProvider, accessTokenIssuer, refreshSessionFactory, refreshTokenRepository);
    }

    @Test
    void deniesSuspendedAndDeactivatedAccounts() {
        for (UserStatus status : Set.of(UserStatus.SUSPENDED, UserStatus.DEACTIVATED)) {
            User user = userWithStatus(status);
            when(userRepository.findByEmail(new Email(EMAIL))).thenReturn(Optional.of(user));
            when(passwordHasher.matches(RAW_PASSWORD, STORED_PASSWORD_HASH)).thenReturn(true);

            assertThrows(
                    AccountOperationNotAllowedException.class,
                    () -> service.login(new LoginCommand(EMAIL, RAW_PASSWORD))
            );
        }

        verify(timeProvider, never()).now();
        verifyNoInteractions(accessTokenIssuer, refreshSessionFactory, refreshTokenRepository);
    }

    private static User userWithStatus(UserStatus status) {
        Instant createdAt = Instant.parse("2026-09-20T12:00:00Z");
        Instant verifiedAt = status == UserStatus.PENDING_EMAIL_VERIFICATION ? null : createdAt;
        return User.reconstitute(
                USER_ID,
                new Email(EMAIL),
                null,
                STORED_PASSWORD_HASH,
                "Anna",
                "Petrova",
                Set.of(UserRole.STUDENT),
                status,
                verifiedAt,
                createdAt,
                createdAt
        );
    }

    private static RefreshSession refreshSession() {
        return new RefreshSession(
                new IssuedRefreshToken("raw-refresh-token", REFRESH_EXPIRES_AT),
                new RefreshTokenState(
                        UUID.fromString("356d79bf-1934-4460-aea2-cadf0616c4a5"),
                        USER_ID,
                        "a".repeat(64),
                        UUID.fromString("68c58628-d634-4f5e-bf35-03998cb005c1"),
                        REFRESH_EXPIRES_AT,
                        null,
                        AUTHENTICATED_AT
                )
        );
    }
}
