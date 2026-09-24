package io.github.edtechdevelopment.identity.application.service.authentication;

import io.github.edtechdevelopment.identity.application.command.authentication.LoginCommand;
import io.github.edtechdevelopment.identity.application.exception.AccountOperationNotAllowedException;
import io.github.edtechdevelopment.identity.application.exception.EmailVerificationRequiredException;
import io.github.edtechdevelopment.identity.application.exception.InvalidCredentialsException;
import io.github.edtechdevelopment.identity.application.exception.InvalidUseCaseInputException;
import io.github.edtechdevelopment.identity.application.model.IssuedAccessToken;
import io.github.edtechdevelopment.identity.application.model.RefreshSession;
import io.github.edtechdevelopment.identity.application.port.in.authentication.LoginUseCase;
import io.github.edtechdevelopment.identity.application.port.out.TimeProvider;
import io.github.edtechdevelopment.identity.application.port.out.persistence.RefreshTokenRepository;
import io.github.edtechdevelopment.identity.application.port.out.persistence.UserRepository;
import io.github.edtechdevelopment.identity.application.port.out.security.AccessTokenIssuer;
import io.github.edtechdevelopment.identity.application.port.out.security.PasswordHasher;
import io.github.edtechdevelopment.identity.application.result.AuthenticationResult;
import io.github.edtechdevelopment.identity.domain.user.exception.InvalidEmailException;
import io.github.edtechdevelopment.identity.domain.user.model.Email;
import io.github.edtechdevelopment.identity.domain.user.model.PasswordHash;
import io.github.edtechdevelopment.identity.domain.user.model.User;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class LoginService implements LoginUseCase {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 72;
    private static final char FIRST_ALLOWED_PASSWORD_CHARACTER = '!';
    private static final char LAST_ALLOWED_PASSWORD_CHARACTER = '~';
    private static final PasswordHash DUMMY_PASSWORD_HASH = new PasswordHash(
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
    );

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordHasher passwordHasher;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshSessionFactory refreshSessionFactory;
    private final TimeProvider timeProvider;

    public LoginService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordHasher passwordHasher,
            AccessTokenIssuer accessTokenIssuer,
            RefreshSessionFactory refreshSessionFactory,
            TimeProvider timeProvider
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "User repository must not be null");
        this.refreshTokenRepository = Objects.requireNonNull(
                refreshTokenRepository,
                "Refresh token repository must not be null"
        );
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "Password hasher must not be null");
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
    @Transactional
    public AuthenticationResult login(LoginCommand command) {
        validateCommand(command);

        Email email = createEmail(command.email());
        Optional<User> foundUser = userRepository.findByEmail(email);
        PasswordHash passwordHash = foundUser
                .map(User::passwordHash)
                .orElse(DUMMY_PASSWORD_HASH);

        boolean passwordMatches = passwordHasher.matches(command.rawPassword(), passwordHash);
        if (foundUser.isEmpty() || !passwordMatches) {
            throw new InvalidCredentialsException();
        }

        User user = foundUser.orElseThrow();
        requireLoginAllowed(user);

        Instant authenticatedAt = timeProvider.now();
        IssuedAccessToken accessToken = accessTokenIssuer.issue(user, authenticatedAt);
        RefreshSession refreshSession = refreshSessionFactory.create(user.id(), authenticatedAt);

        refreshTokenRepository.save(refreshSession.state());
        return new AuthenticationResult(accessToken, refreshSession.token());
    }

    private static void validateCommand(LoginCommand command) {
        if (command == null) {
            throw new InvalidUseCaseInputException("Login command must not be null");
        }

        String rawPassword = command.rawPassword();
        if (rawPassword == null) {
            throw new InvalidUseCaseInputException("Password must not be null");
        }
        if (rawPassword.length() < MIN_PASSWORD_LENGTH || rawPassword.length() > MAX_PASSWORD_LENGTH) {
            throw new InvalidUseCaseInputException("Password must contain from 8 to 72 characters");
        }
        if (containsForbiddenPasswordCharacter(rawPassword)) {
            throw new InvalidUseCaseInputException(
                    "Password must contain only printable ASCII characters without spaces"
            );
        }
    }

    private static boolean containsForbiddenPasswordCharacter(String rawPassword) {
        return rawPassword.chars().anyMatch(character ->
                character < FIRST_ALLOWED_PASSWORD_CHARACTER || character > LAST_ALLOWED_PASSWORD_CHARACTER
        );
    }

    private static Email createEmail(String rawEmail) {
        try {
            return new Email(rawEmail);
        } catch (InvalidEmailException exception) {
            throw new InvalidUseCaseInputException(exception.getMessage(), exception);
        }
    }

    private static void requireLoginAllowed(User user) {
        switch (user.status()) {
            case ACTIVE -> {
                return;
            }
            case PENDING_EMAIL_VERIFICATION -> throw new EmailVerificationRequiredException();
            case SUSPENDED, DEACTIVATED -> throw new AccountOperationNotAllowedException();
        }
    }
}
