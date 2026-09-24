package io.github.edtechdevelopment.identity.application.service.authentication;

import io.github.edtechdevelopment.identity.application.command.authentication.RefreshTokenCommand;
import io.github.edtechdevelopment.identity.application.exception.InvalidRefreshTokenException;
import io.github.edtechdevelopment.identity.application.exception.RefreshAccessDeniedException;
import io.github.edtechdevelopment.identity.application.model.IssuedAccessToken;
import io.github.edtechdevelopment.identity.application.model.RefreshSession;
import io.github.edtechdevelopment.identity.application.model.RefreshTokenState;
import io.github.edtechdevelopment.identity.application.port.in.authentication.RefreshTokenUseCase;
import io.github.edtechdevelopment.identity.application.port.out.TimeProvider;
import io.github.edtechdevelopment.identity.application.port.out.persistence.RefreshTokenRepository;
import io.github.edtechdevelopment.identity.application.port.out.persistence.UserRepository;
import io.github.edtechdevelopment.identity.application.port.out.security.AccessTokenIssuer;
import io.github.edtechdevelopment.identity.application.port.out.security.RefreshTokenHasher;
import io.github.edtechdevelopment.identity.application.result.AuthenticationResult;
import io.github.edtechdevelopment.identity.domain.user.model.User;
import io.github.edtechdevelopment.identity.domain.user.model.UserStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

public class RefreshTokenService implements RefreshTokenUseCase {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenHasher refreshTokenHasher;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshSessionFactory refreshSessionFactory;
    private final TimeProvider timeProvider;

    public RefreshTokenService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            RefreshTokenHasher refreshTokenHasher,
            AccessTokenIssuer accessTokenIssuer,
            RefreshSessionFactory refreshSessionFactory,
            TimeProvider timeProvider
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "User repository must not be null");
        this.refreshTokenRepository = Objects.requireNonNull(
                refreshTokenRepository,
                "Refresh token repository must not be null"
        );
        this.refreshTokenHasher = Objects.requireNonNull(
                refreshTokenHasher,
                "Refresh token hasher must not be null"
        );
        this.accessTokenIssuer = Objects.requireNonNull(
                accessTokenIssuer,
                "Access token issuer must not be null"
        );
        this.refreshSessionFactory = Objects.requireNonNull(
                refreshSessionFactory,
                "Refresh session factory must not be null"
        );
        this.timeProvider = Objects.requireNonNull(timeProvider, "Time provider must not be null");
    }

    @Override
    @Transactional(noRollbackFor = {
            InvalidRefreshTokenException.class,
            RefreshAccessDeniedException.class
    })
    public AuthenticationResult refresh(RefreshTokenCommand command) {
        String rawRefreshToken = requireRawRefreshToken(command);
        String tokenHash = refreshTokenHasher.hash(rawRefreshToken);
        Instant refreshedAt = timeProvider.now();

        RefreshTokenState initialToken = refreshTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(InvalidRefreshTokenException::new);

        User user = userRepository
                .findByIdForUpdate(initialToken.userId())
                .orElseThrow(InvalidRefreshTokenException::new);

        RefreshTokenState lockedToken = refreshTokenRepository
                .findByTokenHashForUpdate(tokenHash)
                .filter(token -> token.id().equals(initialToken.id()))
                .filter(token -> token.userId().equals(user.id()))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (lockedToken.isRevoked()) {
            revokeFamily(lockedToken, refreshedAt);
            throw new InvalidRefreshTokenException();
        }
        if (!lockedToken.isActiveAt(refreshedAt)) {
            throw new InvalidRefreshTokenException();
        }
        if (user.status() != UserStatus.ACTIVE) {
            revokeFamily(lockedToken, refreshedAt);
            throw new RefreshAccessDeniedException();
        }

        IssuedAccessToken accessToken = accessTokenIssuer.issue(user, refreshedAt);
        RefreshSession rotatedSession = refreshSessionFactory.rotate(
                user.id(),
                lockedToken.familyId(),
                lockedToken.expiresAt(),
                refreshedAt
        );

        refreshTokenRepository.revoke(lockedToken.id(), refreshedAt);
        refreshTokenRepository.save(rotatedSession.state());

        return new AuthenticationResult(accessToken, rotatedSession.token());
    }

    private void revokeFamily(RefreshTokenState token, Instant revokedAt) {
        refreshTokenRepository.revokeFamily(token.userId(), token.familyId(), revokedAt);
    }

    private static String requireRawRefreshToken(RefreshTokenCommand command) {
        if (command == null || command.rawRefreshToken() == null || command.rawRefreshToken().isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        return command.rawRefreshToken();
    }
}
