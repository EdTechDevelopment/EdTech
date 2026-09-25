package io.github.edtechdevelopment.identity.application.service.account;

import io.github.edtechdevelopment.identity.application.command.account.UpdateCurrentUserCommand;
import io.github.edtechdevelopment.identity.application.exception.AccountOperationNotAllowedException;
import io.github.edtechdevelopment.identity.application.exception.EmailAlreadyExistsException;
import io.github.edtechdevelopment.identity.application.exception.InvalidUseCaseInputException;
import io.github.edtechdevelopment.identity.application.mapper.IdentityApiMapper;
import io.github.edtechdevelopment.identity.application.mapper.UserResultMapper;
import io.github.edtechdevelopment.identity.application.port.in.account.UpdateCurrentUserUseCase;
import io.github.edtechdevelopment.identity.application.port.out.TimeProvider;
import io.github.edtechdevelopment.identity.application.port.out.messaging.IntegrationEventPublisher;
import io.github.edtechdevelopment.identity.application.port.out.messaging.VerificationEmailSender;
import io.github.edtechdevelopment.identity.application.port.out.persistence.EmailVerificationRepository;
import io.github.edtechdevelopment.identity.application.port.out.persistence.UserRepository;
import io.github.edtechdevelopment.identity.application.port.out.security.VerificationTokenGenerator;
import io.github.edtechdevelopment.identity.application.port.out.security.VerificationTokenHasher;
import io.github.edtechdevelopment.identity.application.result.CurrentUserResult;
import io.github.edtechdevelopment.identity.domain.user.event.UserAccountUpdatedDomainEvent;
import io.github.edtechdevelopment.identity.domain.user.event.UserDomainEvent;
import io.github.edtechdevelopment.identity.domain.user.exception.InvalidEmailException;
import io.github.edtechdevelopment.identity.domain.user.exception.InvalidUserDataException;
import io.github.edtechdevelopment.identity.domain.user.model.Email;
import io.github.edtechdevelopment.identity.domain.user.model.User;
import io.github.edtechdevelopment.identity.domain.user.model.UserStatus;
import io.github.edtechdevelopment.identity.domain.verification.model.EmailVerification;
import io.github.edtechdevelopment.identity.domain.verification.model.VerificationPurpose;
import io.github.edtechdevelopment.identity.domain.verification.model.VerificationTokenHash;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class UpdateCurrentUserService implements UpdateCurrentUserUseCase {

    private final UserRepository userRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final VerificationTokenGenerator verificationTokenGenerator;
    private final VerificationTokenHasher verificationTokenHasher;
    private final VerificationEmailSender verificationEmailSender;
    private final IntegrationEventPublisher integrationEventPublisher;
    private final TimeProvider timeProvider;
    private final UserResultMapper userResultMapper;
    private final IdentityApiMapper identityApiMapper;
    private final Duration verificationTokenTtl;

    public UpdateCurrentUserService(
            UserRepository userRepository,
            EmailVerificationRepository emailVerificationRepository,
            VerificationTokenGenerator verificationTokenGenerator,
            VerificationTokenHasher verificationTokenHasher,
            VerificationEmailSender verificationEmailSender,
            IntegrationEventPublisher integrationEventPublisher,
            TimeProvider timeProvider,
            UserResultMapper userResultMapper,
            IdentityApiMapper identityApiMapper,
            Duration verificationTokenTtl
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "User repository must not be null");
        this.emailVerificationRepository = Objects.requireNonNull(
                emailVerificationRepository,
                "Email verification repository must not be null"
        );
        this.verificationTokenGenerator = Objects.requireNonNull(
                verificationTokenGenerator,
                "Verification token generator must not be null"
        );
        this.verificationTokenHasher = Objects.requireNonNull(
                verificationTokenHasher,
                "Verification token hasher must not be null"
        );
        this.verificationEmailSender = Objects.requireNonNull(
                verificationEmailSender,
                "Verification email sender must not be null"
        );
        this.integrationEventPublisher = Objects.requireNonNull(
                integrationEventPublisher,
                "Integration event publisher must not be null"
        );
        this.timeProvider = Objects.requireNonNull(timeProvider, "Time provider must not be null");
        this.userResultMapper = Objects.requireNonNull(userResultMapper, "User result mapper must not be null");
        this.identityApiMapper = Objects.requireNonNull(identityApiMapper, "Identity API mapper must not be null");
        this.verificationTokenTtl = requirePositiveDuration(verificationTokenTtl);
    }

    @Override
    @Transactional
    public CurrentUserResult updateCurrentUser(UpdateCurrentUserCommand command) {
        validateCommand(command);
        Email requestedEmail = createOptionalEmail(command.email());
        Instant changedAt = timeProvider.now();

        User user = userRepository
                .findByIdForUpdate(command.userId())
                .orElseThrow(AccountOperationNotAllowedException::new);
        requireActiveUser(user);

        if (command.firstName() != null || command.lastName() != null) {
            updateProfile(user, command.firstName(), command.lastName(), changedAt);
        }

        Email newPendingEmail = determineNewPendingEmail(user, requestedEmail);
        EmailVerification verification = null;
        String rawVerificationToken = null;
        Instant verificationExpiresAt = null;

        if (newPendingEmail != null) {
            ensureEmailIsAvailable(newPendingEmail);
            requestEmailChange(user, newPendingEmail, changedAt);

            emailVerificationRepository.invalidateActiveForUser(
                    user.id(),
                    VerificationPurpose.EMAIL_CHANGE,
                    changedAt
            );

            rawVerificationToken = verificationTokenGenerator.generate();
            VerificationTokenHash tokenHash = verificationTokenHasher.hash(rawVerificationToken);
            verificationExpiresAt = changedAt.plus(verificationTokenTtl);
            verification = EmailVerification.create(
                    UUID.randomUUID(),
                    user.id(),
                    newPendingEmail,
                    tokenHash,
                    VerificationPurpose.EMAIL_CHANGE,
                    changedAt,
                    verificationExpiresAt
            );
        }

        List<UserDomainEvent> domainEvents = user.pullDomainEvents();
        if (!domainEvents.isEmpty()) {
            userRepository.save(user);
        }

        if (verification != null) {
            emailVerificationRepository.save(verification);
            verificationEmailSender.sendVerificationEmail(
                    newPendingEmail,
                    rawVerificationToken,
                    VerificationPurpose.EMAIL_CHANGE,
                    verificationExpiresAt
            );
        }

        publishDomainEvents(domainEvents);
        return userResultMapper.toCurrentUserResult(user);
    }

    private Email determineNewPendingEmail(User user, Email requestedEmail) {
        if (requestedEmail == null || requestedEmail.equals(user.email())) {
            return null;
        }
        if (user.pendingEmail().filter(requestedEmail::equals).isPresent()) {
            return null;
        }
        return requestedEmail;
    }

    private void ensureEmailIsAvailable(Email email) {
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException();
        }
    }

    private void publishDomainEvents(List<UserDomainEvent> domainEvents) {
        for (UserDomainEvent domainEvent : domainEvents) {
            if (domainEvent instanceof UserAccountUpdatedDomainEvent updatedEvent) {
                integrationEventPublisher.publish(identityApiMapper.toIntegrationEvent(updatedEvent));
                continue;
            }
            throw new IllegalStateException("User update produced an unsupported domain event");
        }
    }

    private static void validateCommand(UpdateCurrentUserCommand command) {
        if (command == null) {
            throw new InvalidUseCaseInputException("Update current user command must not be null");
        }
        if (command.userId() == null) {
            throw new InvalidUseCaseInputException("Current user id must not be null");
        }
        if (command.email() == null && command.firstName() == null && command.lastName() == null) {
            throw new InvalidUseCaseInputException("At least one account field must be provided");
        }
    }

    private static Email createOptionalEmail(String rawEmail) {
        if (rawEmail == null) {
            return null;
        }

        try {
            return new Email(rawEmail);
        } catch (InvalidEmailException exception) {
            throw new InvalidUseCaseInputException(exception.getMessage(), exception);
        }
    }

    private static void requireActiveUser(User user) {
        if (user.status() != UserStatus.ACTIVE) {
            throw new AccountOperationNotAllowedException();
        }
    }

    private static void updateProfile(User user, String firstName, String lastName, Instant changedAt) {
        try {
            user.updateProfile(firstName, lastName, changedAt);
        } catch (InvalidUserDataException exception) {
            throw new InvalidUseCaseInputException(exception.getMessage(), exception);
        }
    }

    private static void requestEmailChange(User user, Email email, Instant changedAt) {
        try {
            user.requestEmailChange(email, changedAt);
        } catch (InvalidUserDataException exception) {
            throw new InvalidUseCaseInputException(exception.getMessage(), exception);
        }
    }

    private static Duration requirePositiveDuration(Duration duration) {
        Objects.requireNonNull(duration, "Verification token TTL must not be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("Verification token TTL must be positive");
        }
        return duration;
    }
}
