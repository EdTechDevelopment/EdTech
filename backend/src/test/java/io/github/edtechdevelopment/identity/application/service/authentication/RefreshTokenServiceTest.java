package io.github.edtechdevelopment.identity.application.service.authentication;

import io.github.edtechdevelopment.identity.application.command.authentication.RefreshTokenCommand;
import io.github.edtechdevelopment.identity.application.exception.InvalidRefreshTokenException;
import io.github.edtechdevelopment.identity.application.exception.RefreshAccessDeniedException;
import io.github.edtechdevelopment.identity.application.model.IssuedAccessToken;
import io.github.edtechdevelopment.identity.application.model.IssuedRefreshToken;
import io.github.edtechdevelopment.identity.application.model.RefreshSession;
import io.github.edtechdevelopment.identity.application.model.RefreshTokenState;
import io.github.edtechdevelopment.identity.application.port.out.TimeProvider;
import io.github.edtechdevelopment.identity.application.port.out.persistence.RefreshTokenRepository;
import io.github.edtechdevelopment.identity.application.port.out.persistence.UserRepository;
import io.github.edtechdevelopment.identity.application.port.out.security.AccessTokenIssuer;
import io.github.edtechdevelopment.identity.application.port.out.security.RefreshTokenHasher;
import io.github.edtechdevelopment.identity.application.result.AuthenticationResult;
import io.github.edtechdevelopment.identity.domain.user.model.Email;
import io.github.edtechdevelopment.identity.domain.user.model.PasswordHash;
import io.github.edtechdevelopment.identity.domain.user.model.User;
import io.github.edtechdevelopment.identity.domain.user.model.UserRole;
import io.github.edtechdevelopment.identity.domain.user.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.time.LocalDate;
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

class RefreshTokenServiceTest {

    private static final UUID USER_ID = UUID.fromString("f20fd1ed-f2ab-4ceb-b8d3-58593fc9204c");
    private static final UUID TOKEN_ID = UUID.fromString("2ea76529-da8b-4a83-921f-c5a258018f42");
    private static final UUID FAMILY_ID = UUID.fromString("7bd84e3e-1038-450c-8011-dcb2a455d38e");
    private static final Instant CREATED_AT = Instant.parse("2026-09-24T10:00:00Z");
    private static final Instant REFRESHED_AT = Instant.parse("2026-09-25T10:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-10-24T10:00:00Z");
    private static final Instant ACCESS_EXPIRES_AT = Instant.parse("2026-09-25T10:15:00Z");
    private static final String RAW_REFRESH_TOKEN = "current-raw-refresh-token";
    private static final String TOKEN_HASH = "a".repeat(64);

    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private RefreshTokenHasher refreshTokenHasher;
    private AccessTokenIssuer accessTokenIssuer;
    private RefreshSessionFactory refreshSessionFactory;
    private TimeProvider timeProvider;
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        refreshTokenHasher = mock(RefreshTokenHasher.class);
        accessTokenIssuer = mock(AccessTokenIssuer.class);
        refreshSessionFactory = mock(RefreshSessionFactory.class);
        timeProvider = mock(TimeProvider.class);
        service = new RefreshTokenService(
                userRepository,
                refreshTokenRepository,
                refreshTokenHasher,
                accessTokenIssuer,
                refreshSessionFactory,
                timeProvider
        );
    }

    @Test
    void rotatesActiveTokenInTheSameFamilyWithTheSameExpiration() {
        User user = userWithStatus(UserStatus.ACTIVE);
        RefreshTokenState currentToken = tokenState(null, EXPIRES_AT);
        RefreshSession rotatedSession = rotatedSession();
        stubLockedToken(currentToken, user);
        when(accessTokenIssuer.issue(user, REFRESHED_AT))
                .thenReturn(new IssuedAccessToken("new.signed.jwt", ACCESS_EXPIRES_AT));
        when(refreshSessionFactory.rotate(USER_ID, FAMILY_ID, EXPIRES_AT, REFRESHED_AT))
                .thenReturn(rotatedSession);

        AuthenticationResult result = service.refresh(new RefreshTokenCommand(RAW_REFRESH_TOKEN));

        InOrder lockOrder = inOrder(refreshTokenRepository, userRepository);
        lockOrder.verify(refreshTokenRepository).findByTokenHash(TOKEN_HASH);
        lockOrder.verify(userRepository).findByIdForUpdate(USER_ID);
        lockOrder.verify(refreshTokenRepository).findByTokenHashForUpdate(TOKEN_HASH);

        InOrder writeOrder = inOrder(refreshTokenRepository);
        writeOrder.verify(refreshTokenRepository).revoke(TOKEN_ID, REFRESHED_AT);
        writeOrder.verify(refreshTokenRepository).save(rotatedSession.state());

        assertEquals("new.signed.jwt", result.accessToken().value());
        assertEquals("rotated-raw-refresh-token", result.refreshToken().value());
        assertEquals(FAMILY_ID, rotatedSession.state().familyId());
        assertEquals(EXPIRES_AT, rotatedSession.state().expiresAt());
    }

    @Test
    void revokedTokenRevokesItsWholeFamilyAsReuseDetection() {
        Instant revokedAt = Instant.parse("2026-09-24T11:00:00Z");
        RefreshTokenState revokedToken = tokenState(revokedAt, EXPIRES_AT);
        User user = userWithStatus(UserStatus.ACTIVE);
        stubLockedToken(revokedToken, user);

        assertThrows(
                InvalidRefreshTokenException.class,
                () -> service.refresh(new RefreshTokenCommand(RAW_REFRESH_TOKEN))
        );

        verify(refreshTokenRepository).revokeFamily(USER_ID, FAMILY_ID, REFRESHED_AT);
        verifyNoInteractions(accessTokenIssuer, refreshSessionFactory);
    }

    @Test
    void expiredTokenDoesNotCreateAnotherToken() {
        RefreshTokenState expiredToken = tokenState(null, REFRESHED_AT);
        User user = userWithStatus(UserStatus.ACTIVE);
        stubLockedToken(expiredToken, user);

        assertThrows(
                InvalidRefreshTokenException.class,
                () -> service.refresh(new RefreshTokenCommand(RAW_REFRESH_TOKEN))
        );

        verify(refreshTokenRepository, never()).revokeFamily(USER_ID, FAMILY_ID, REFRESHED_AT);
        verifyNoInteractions(accessTokenIssuer, refreshSessionFactory);
    }

    @Test
    void nonActiveUserRevokesPresentedFamilyAndReturnsForbidden() {
        RefreshTokenState activeToken = tokenState(null, EXPIRES_AT);
        User suspendedUser = userWithStatus(UserStatus.SUSPENDED);
        stubLockedToken(activeToken, suspendedUser);

        assertThrows(
                RefreshAccessDeniedException.class,
                () -> service.refresh(new RefreshTokenCommand(RAW_REFRESH_TOKEN))
        );

        verify(refreshTokenRepository).revokeFamily(USER_ID, FAMILY_ID, REFRESHED_AT);
        verifyNoInteractions(accessTokenIssuer, refreshSessionFactory);
    }

    @Test
    void rejectsTokenThatChangedBetweenInitialAndLockingReads() {
        RefreshTokenState initialToken = tokenState(null, EXPIRES_AT);
        RefreshTokenState differentToken = new RefreshTokenState(
                UUID.fromString("b59ae58f-abcb-41c5-8867-41dde15bf712"),
                USER_ID,
                TOKEN_HASH,
                FAMILY_ID,
                EXPIRES_AT,
                null,
                CREATED_AT
        );
        User user = userWithStatus(UserStatus.ACTIVE);
        when(refreshTokenHasher.hash(RAW_REFRESH_TOKEN)).thenReturn(TOKEN_HASH);
        when(timeProvider.now()).thenReturn(REFRESHED_AT);
        when(refreshTokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(initialToken));
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.findByTokenHashForUpdate(TOKEN_HASH)).thenReturn(Optional.of(differentToken));

        assertThrows(
                InvalidRefreshTokenException.class,
                () -> service.refresh(new RefreshTokenCommand(RAW_REFRESH_TOKEN))
        );

        verifyNoInteractions(accessTokenIssuer, refreshSessionFactory);
    }

    @Test
    void missingTokenIsRejectedBeforeAnyDependencyIsCalled() {
        assertThrows(
                InvalidRefreshTokenException.class,
                () -> service.refresh(new RefreshTokenCommand(null))
        );

        verifyNoInteractions(
                userRepository,
                refreshTokenRepository,
                refreshTokenHasher,
                accessTokenIssuer,
                refreshSessionFactory,
                timeProvider
        );
    }

    private void stubLockedToken(RefreshTokenState token, User user) {
        when(refreshTokenHasher.hash(RAW_REFRESH_TOKEN)).thenReturn(TOKEN_HASH);
        when(timeProvider.now()).thenReturn(REFRESHED_AT);
        when(refreshTokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(token));
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.findByTokenHashForUpdate(TOKEN_HASH)).thenReturn(Optional.of(token));
    }

    private static RefreshTokenState tokenState(Instant revokedAt, Instant expiresAt) {
        return new RefreshTokenState(
                TOKEN_ID,
                USER_ID,
                TOKEN_HASH,
                FAMILY_ID,
                expiresAt,
                revokedAt,
                CREATED_AT
        );
    }

    private static RefreshSession rotatedSession() {
        return new RefreshSession(
                new IssuedRefreshToken("rotated-raw-refresh-token", EXPIRES_AT),
                new RefreshTokenState(
                        UUID.fromString("7e13c4da-d514-4a89-89eb-2893fdf6fd7e"),
                        USER_ID,
                        "b".repeat(64),
                        FAMILY_ID,
                        EXPIRES_AT,
                        null,
                        REFRESHED_AT
                )
        );
    }

    private static User userWithStatus(UserStatus status) {
        Instant userCreatedAt = CREATED_AT.minusSeconds(3600);
        Instant verifiedAt = status == UserStatus.PENDING_EMAIL_VERIFICATION ? null : userCreatedAt;
        return User.reconstitute(
                USER_ID,
                new Email("anna@example.com"),
                null,
                new PasswordHash("stored-password-hash"),
                "Anna",
                "Petrova",
                LocalDate.of(2000, 1, 1),
                Set.of(UserRole.STUDENT),
                status,
                verifiedAt,
                userCreatedAt,
                userCreatedAt
        );
    }
}
