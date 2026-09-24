package io.github.edtechdevelopment.identity.application.service.verification;

import io.github.edtechdevelopment.identity.application.command.verification.ConfirmEmailCommand;
import io.github.edtechdevelopment.identity.application.exception.InvalidUseCaseInputException;
import io.github.edtechdevelopment.identity.application.exception.InvalidVerificationTokenException;
import io.github.edtechdevelopment.identity.application.mapper.IdentityApiMapper;
import io.github.edtechdevelopment.identity.application.model.IssuedAccessToken;
import io.github.edtechdevelopment.identity.application.model.RefreshSession;
import io.github.edtechdevelopment.identity.application.port.in.verification.ConfirmEmailUseCase;
import io.github.edtechdevelopment.identity.application.port.out.TimeProvider;
import io.github.edtechdevelopment.identity.application.port.out.messaging.IntegrationEventPublisher;
import io.github.edtechdevelopment.identity.application.port.out.persistence.EmailVerificationRepository;
import io.github.edtechdevelopment.identity.application.port.out.persistence.RefreshTokenRepository;
import io.github.edtechdevelopment.identity.application.port.out.persistence.UserRepository;
import io.github.edtechdevelopment.identity.application.port.out.security.AccessTokenIssuer;
import io.github.edtechdevelopment.identity.application.port.out.security.VerificationTokenHasher;
import io.github.edtechdevelopment.identity.application.result.AuthenticationResult;
import io.github.edtechdevelopment.identity.application.service.authentication.RefreshSessionFactory;
import io.github.edtechdevelopment.identity.domain.user.event.UserAccountUpdatedDomainEvent;
import io.github.edtechdevelopment.identity.domain.user.event.UserActivatedDomainEvent;
import io.github.edtechdevelopment.identity.domain.user.event.UserDomainEvent;
import io.github.edtechdevelopment.identity.domain.user.exception.InvalidUserStateException;
import io.github.edtechdevelopment.identity.domain.user.model.User;
import io.github.edtechdevelopment.identity.domain.verification.exception.InvalidEmailVerificationException;
import io.github.edtechdevelopment.identity.domain.verification.model.EmailVerification;
import io.github.edtechdevelopment.identity.domain.verification.model.VerificationPurpose;
import io.github.edtechdevelopment.identity.domain.verification.model.VerificationTokenHash;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class ConfirmEmailService implements ConfirmEmailUseCase {

    private final UserRepository userRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final VerificationTokenHasher verificationTokenHasher;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshSessionFactory refreshSessionFactory;
    private final IntegrationEventPublisher integrationEventPublisher;
    private final TimeProvider timeProvider;
    private final IdentityApiMapper identityApiMapper;

    public ConfirmEmailService(
            UserRepository userRepository,
            EmailVerificationRepository emailVerificationRepository,
            RefreshTokenRepository refreshTokenRepository,
            VerificationTokenHasher verificationTokenHasher,
            AccessTokenIssuer accessTokenIssuer,
            RefreshSessionFactory refreshSessionFactory,
            IntegrationEventPublisher integrationEventPublisher,
            TimeProvider timeProvider,
            IdentityApiMapper identityApiMapper
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "User repository must not be null");
        this.emailVerificationRepository = Objects.requireNonNull(
                emailVerificationRepository,
                "Email verification repository must not be null"
        );
        this.refreshTokenRepository = Objects.requireNonNull(
                refreshTokenRepository,
                "Refresh token repository must not be null"
        );
        this.verificationTokenHasher = Objects.requireNonNull(
                verificationTokenHasher,
                "Verification token hasher must not be null"
        );
        this.accessTokenIssuer = Objects.requireNonNull(accessTokenIssuer, "Access token issuer must not be null");
        this.refreshSessionFactory = Objects.requireNonNull(
                refreshSessionFactory,
                "Refresh session factory must not be null"
        );
        this.integrationEventPublisher = Objects.requireNonNull(
                integrationEventPublisher,
                "Integration event publisher must not be null"
        );
        this.timeProvider = Objects.requireNonNull(timeProvider, "Time provider must not be null");
        this.identityApiMapper = Objects.requireNonNull(identityApiMapper, "Identity API mapper must not be null");
    }

    @Override
    @Transactional
    public AuthenticationResult confirmEmail(ConfirmEmailCommand command) {
        String rawToken = requireRawToken(command);
        Instant confirmedAt = timeProvider.now();
        VerificationTokenHash tokenHash = verificationTokenHasher.hash(rawToken);

        EmailVerification initialVerification = emailVerificationRepository
                .findActiveByTokenHash(tokenHash, confirmedAt)
                .orElseThrow(InvalidVerificationTokenException::new);

        User user = userRepository
                .findByIdForUpdate(initialVerification.userId())
                .orElseThrow(InvalidVerificationTokenException::new);

        EmailVerification lockedVerification = emailVerificationRepository
                .findActiveByTokenHashForUpdate(tokenHash, confirmedAt)
                .filter(verification -> verification.id().equals(initialVerification.id()))
                .filter(verification -> verification.userId().equals(user.id()))
                .orElseThrow(InvalidVerificationTokenException::new);

        confirmUserEmail(user, lockedVerification, confirmedAt);
        List<UserDomainEvent> domainEvents = user.pullDomainEvents();

        IssuedAccessToken accessToken = accessTokenIssuer.issue(user, confirmedAt);
        RefreshSession refreshSession = refreshSessionFactory.create(user.id(), confirmedAt);

        userRepository.save(user);
        emailVerificationRepository.save(lockedVerification);
        refreshTokenRepository.save(refreshSession.state());

        publishDomainEvents(domainEvents);
        integrationEventPublisher.publish(
                identityApiMapper.toIntegrationEvent(lockedVerification, confirmedAt)
        );

        return new AuthenticationResult(accessToken, refreshSession.token());
    }

    private void confirmUserEmail(User user, EmailVerification verification, Instant confirmedAt) {
        try {
            if (verification.purpose() == VerificationPurpose.REGISTRATION) {
                user.verifyRegistrationEmail(verification.targetEmail(), confirmedAt);
            } else {
                user.confirmPendingEmail(verification.targetEmail(), confirmedAt);
            }
            verification.consume(confirmedAt);
        } catch (InvalidUserStateException | InvalidEmailVerificationException exception) {
            throw new InvalidVerificationTokenException(exception);
        }
    }

    private void publishDomainEvents(List<UserDomainEvent> domainEvents) {
        if (domainEvents.size() != 1) {
            throw new IllegalStateException("Email confirmation must produce exactly one user domain event");
        }

        UserDomainEvent domainEvent = domainEvents.getFirst();
        if (domainEvent instanceof UserActivatedDomainEvent activatedEvent) {
            integrationEventPublisher.publish(identityApiMapper.toIntegrationEvent(activatedEvent));
            return;
        }
        if (domainEvent instanceof UserAccountUpdatedDomainEvent updatedEvent) {
            integrationEventPublisher.publish(identityApiMapper.toIntegrationEvent(updatedEvent));
            return;
        }
        throw new IllegalStateException("Email confirmation produced an unsupported user domain event");
    }

    private static String requireRawToken(ConfirmEmailCommand command) {
        if (command == null) {
            throw new InvalidUseCaseInputException("Confirm email command must not be null");
        }
        if (command.rawToken() == null || command.rawToken().isBlank()) {
            throw new InvalidUseCaseInputException("Verification token must not be blank");
        }
        return command.rawToken();
    }

}
