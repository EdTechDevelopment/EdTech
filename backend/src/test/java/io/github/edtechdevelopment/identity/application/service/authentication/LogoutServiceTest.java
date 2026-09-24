package io.github.edtechdevelopment.identity.application.service.authentication;

import io.github.edtechdevelopment.identity.application.command.authentication.LogoutCommand;
import io.github.edtechdevelopment.identity.application.model.RefreshTokenState;
import io.github.edtechdevelopment.identity.application.port.out.TimeProvider;
import io.github.edtechdevelopment.identity.application.port.out.persistence.RefreshTokenRepository;
import io.github.edtechdevelopment.identity.application.port.out.persistence.UserRepository;
import io.github.edtechdevelopment.identity.application.port.out.security.RefreshTokenHasher;
import io.github.edtechdevelopment.identity.domain.user.model.Email;
import io.github.edtechdevelopment.identity.domain.user.model.PasswordHash;
import io.github.edtechdevelopment.identity.domain.user.model.User;
import io.github.edtechdevelopment.identity.domain.user.model.UserRole;
import io.github.edtechdevelopment.identity.domain.user.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LogoutServiceTest {

    private static final UUID USER_ID = UUID.fromString("0ad54e30-45c1-48df-9524-8006b998f2c6");
    private static final UUID TOKEN_ID = UUID.fromString("e81e34a3-12fc-4af4-b6b1-1d409bf620a7");
    private static final UUID FAMILY_ID = UUID.fromString("73b6997e-b081-4330-ad22-714f1a39fd51");
    private static final String RAW_REFRESH_TOKEN = "raw-refresh-token";
    private static final String TOKEN_HASH = "a".repeat(64);
    private static final Instant CREATED_AT = Instant.parse("2026-09-20T10:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-10-20T10:00:00Z");
    private static final Instant LOGGED_OUT_AT = Instant.parse("2026-09-24T10:00:00Z");

    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private RefreshTokenHasher refreshTokenHasher;
    private TimeProvider timeProvider;
    private LogoutService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        refreshTokenHasher = mock(RefreshTokenHasher.class);
        timeProvider = mock(TimeProvider.class);
        service = new LogoutService(
                userRepository,
                refreshTokenRepository,
                refreshTokenHasher,
                timeProvider
        );
    }

    @Test
    void revokesTheWholePresentedTokenFamilyUsingTheSharedLockOrder() {
        RefreshTokenState token = tokenState(null);
        User user = activeUser();
        when(refreshTokenHasher.hash(RAW_REFRESH_TOKEN)).thenReturn(TOKEN_HASH);
        when(refreshTokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(token));
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.findByTokenHashForUpdate(TOKEN_HASH)).thenReturn(Optional.of(token));
        when(timeProvider.now()).thenReturn(LOGGED_OUT_AT);

        service.logout(new LogoutCommand(RAW_REFRESH_TOKEN));

        InOrder lockOrder = inOrder(refreshTokenRepository, userRepository);
        lockOrder.verify(refreshTokenRepository).findByTokenHash(TOKEN_HASH);
        lockOrder.verify(userRepository).findByIdForUpdate(USER_ID);
        lockOrder.verify(refreshTokenRepository).findByTokenHashForUpdate(TOKEN_HASH);
        verify(refreshTokenRepository).revokeFamily(USER_ID, FAMILY_ID, LOGGED_OUT_AT);
    }

    @Test
    void revokedTokenStillRevokesItsWholeFamily() {
        RefreshTokenState revokedToken = tokenState(Instant.parse("2026-09-23T10:00:00Z"));
        User user = activeUser();
        when(refreshTokenHasher.hash(RAW_REFRESH_TOKEN)).thenReturn(TOKEN_HASH);
        when(refreshTokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(revokedToken));
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.findByTokenHashForUpdate(TOKEN_HASH)).thenReturn(Optional.of(revokedToken));
        when(timeProvider.now()).thenReturn(LOGGED_OUT_AT);

        service.logout(new LogoutCommand(RAW_REFRESH_TOKEN));

        verify(refreshTokenRepository).revokeFamily(USER_ID, FAMILY_ID, LOGGED_OUT_AT);
    }

    @Test
    void missingTokenCompletesWithoutCallingDependencies() {
        service.logout(new LogoutCommand(null));

        verifyNoInteractions(userRepository, refreshTokenRepository, refreshTokenHasher, timeProvider);
    }

    @Test
    void unknownTokenCompletesWithoutTryingToLockUser() {
        when(refreshTokenHasher.hash(RAW_REFRESH_TOKEN)).thenReturn(TOKEN_HASH);
        when(refreshTokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.empty());

        service.logout(new LogoutCommand(RAW_REFRESH_TOKEN));

        verifyNoInteractions(userRepository, timeProvider);
        verify(refreshTokenRepository, never()).findByTokenHashForUpdate(TOKEN_HASH);
        verify(refreshTokenRepository, never()).revokeFamily(USER_ID, FAMILY_ID, LOGGED_OUT_AT);
    }

    @Test
    void tokenChangedBetweenReadsCompletesWithoutRevokingAnotherFamily() {
        RefreshTokenState initialToken = tokenState(null);
        RefreshTokenState differentToken = new RefreshTokenState(
                UUID.fromString("d0680459-b021-4e52-9e91-d88da67b6a64"),
                USER_ID,
                TOKEN_HASH,
                UUID.fromString("65c98c45-195c-4e72-a487-718c00710227"),
                EXPIRES_AT,
                null,
                CREATED_AT
        );
        when(refreshTokenHasher.hash(RAW_REFRESH_TOKEN)).thenReturn(TOKEN_HASH);
        when(refreshTokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(initialToken));
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(activeUser()));
        when(refreshTokenRepository.findByTokenHashForUpdate(TOKEN_HASH)).thenReturn(Optional.of(differentToken));

        service.logout(new LogoutCommand(RAW_REFRESH_TOKEN));

        verify(refreshTokenRepository, never()).revokeFamily(USER_ID, FAMILY_ID, LOGGED_OUT_AT);
        verifyNoInteractions(timeProvider);
    }

    private static RefreshTokenState tokenState(Instant revokedAt) {
        return new RefreshTokenState(
                TOKEN_ID,
                USER_ID,
                TOKEN_HASH,
                FAMILY_ID,
                EXPIRES_AT,
                revokedAt,
                CREATED_AT
        );
    }

    private static User activeUser() {
        return User.reconstitute(
                USER_ID,
                new Email("logout@example.com"),
                null,
                new PasswordHash("stored-password-hash"),
                "Logout",
                "User",
                Set.of(UserRole.STUDENT),
                UserStatus.ACTIVE,
                CREATED_AT,
                CREATED_AT,
                CREATED_AT
        );
    }
}
