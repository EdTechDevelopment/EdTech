package io.github.edtechdevelopment.identity.application.service.authentication;

import io.github.edtechdevelopment.identity.application.command.authentication.LogoutCommand;
import io.github.edtechdevelopment.identity.application.model.RefreshTokenState;
import io.github.edtechdevelopment.identity.application.port.in.authentication.LogoutUseCase;
import io.github.edtechdevelopment.identity.application.port.out.TimeProvider;
import io.github.edtechdevelopment.identity.application.port.out.persistence.RefreshTokenRepository;
import io.github.edtechdevelopment.identity.application.port.out.persistence.UserRepository;
import io.github.edtechdevelopment.identity.application.port.out.security.RefreshTokenHasher;
import io.github.edtechdevelopment.identity.domain.user.model.User;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

public class LogoutService implements LogoutUseCase {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenHasher refreshTokenHasher;
    private final TimeProvider timeProvider;

    public LogoutService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            RefreshTokenHasher refreshTokenHasher,
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
        this.timeProvider = Objects.requireNonNull(timeProvider, "Time provider must not be null");
    }

    @Override
    @Transactional
    public void logout(LogoutCommand command) {
        String rawRefreshToken = rawRefreshToken(command);
        if (rawRefreshToken == null) {
            return;
        }

        String tokenHash = refreshTokenHasher.hash(rawRefreshToken);
        RefreshTokenState initialToken = refreshTokenRepository.findByTokenHash(tokenHash).orElse(null);
        if (initialToken == null) {
            return;
        }

        User user = userRepository.findByIdForUpdate(initialToken.userId()).orElse(null);
        if (user == null) {
            return;
        }

        RefreshTokenState lockedToken = refreshTokenRepository
                .findByTokenHashForUpdate(tokenHash)
                .filter(token -> token.id().equals(initialToken.id()))
                .filter(token -> token.userId().equals(user.id()))
                .orElse(null);
        if (lockedToken == null) {
            return;
        }

        Instant loggedOutAt = timeProvider.now();
        refreshTokenRepository.revokeFamily(
                lockedToken.userId(),
                lockedToken.familyId(),
                loggedOutAt
        );
    }

    private static String rawRefreshToken(LogoutCommand command) {
        if (command == null || command.rawRefreshToken() == null || command.rawRefreshToken().isBlank()) {
            return null;
        }
        return command.rawRefreshToken();
    }
}
