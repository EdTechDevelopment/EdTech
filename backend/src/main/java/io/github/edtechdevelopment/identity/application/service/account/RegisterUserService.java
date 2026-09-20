package io.github.edtechdevelopment.identity.application.service.account;

import io.github.edtechdevelopment.identity.api.event.UserRegisteredEvent;
import io.github.edtechdevelopment.identity.application.command.account.RegistrationRole;
import io.github.edtechdevelopment.identity.application.command.account.RegisterUserCommand;
import io.github.edtechdevelopment.identity.application.exception.EmailAlreadyExistsException;
import io.github.edtechdevelopment.identity.application.exception.InvalidUseCaseInputException;
import io.github.edtechdevelopment.identity.application.mapper.IdentityApiMapper;
import io.github.edtechdevelopment.identity.application.port.in.account.RegisterUserUseCase;
import io.github.edtechdevelopment.identity.application.port.out.TimeProvider;
import io.github.edtechdevelopment.identity.application.port.out.messaging.IntegrationEventPublisher;
import io.github.edtechdevelopment.identity.application.port.out.messaging.VerificationEmailSender;
import io.github.edtechdevelopment.identity.application.port.out.persistence.EmailVerificationRepository;
import io.github.edtechdevelopment.identity.application.port.out.persistence.UserRepository;
import io.github.edtechdevelopment.identity.application.port.out.security.PasswordHasher;
import io.github.edtechdevelopment.identity.application.port.out.security.VerificationTokenGenerator;
import io.github.edtechdevelopment.identity.application.port.out.security.VerificationTokenHasher;
import io.github.edtechdevelopment.identity.application.result.RegistrationResult;
import io.github.edtechdevelopment.identity.domain.user.event.UserDomainEvent;
import io.github.edtechdevelopment.identity.domain.user.event.UserRegisteredDomainEvent;
import io.github.edtechdevelopment.identity.domain.user.exception.InvalidEmailException;
import io.github.edtechdevelopment.identity.domain.user.exception.InvalidUserDataException;
import io.github.edtechdevelopment.identity.domain.user.model.Email;
import io.github.edtechdevelopment.identity.domain.user.model.PasswordHash;
import io.github.edtechdevelopment.identity.domain.user.model.User;
import io.github.edtechdevelopment.identity.domain.user.model.UserRole;
import io.github.edtechdevelopment.identity.domain.verification.model.EmailVerification;
import io.github.edtechdevelopment.identity.domain.verification.model.VerificationPurpose;
import io.github.edtechdevelopment.identity.domain.verification.model.VerificationTokenHash;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class RegisterUserService implements RegisterUserUseCase {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 72;
    private static final char FIRST_ALLOWED_PASSWORD_CHARACTER = '!';
    private static final char LAST_ALLOWED_PASSWORD_CHARACTER = '~';

    private final UserRepository userRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final PasswordHasher passwordHasher;
    private final VerificationTokenGenerator verificationTokenGenerator;
    private final VerificationTokenHasher verificationTokenHasher;
    private final VerificationEmailSender verificationEmailSender;
    private final IntegrationEventPublisher integrationEventPublisher;
    private final TimeProvider timeProvider;
    private final IdentityApiMapper identityApiMapper;
    private final Duration verificationTokenTtl;

    public RegisterUserService(
            UserRepository userRepository,
            EmailVerificationRepository emailVerificationRepository,
            PasswordHasher passwordHasher,
            VerificationTokenGenerator verificationTokenGenerator,
            VerificationTokenHasher verificationTokenHasher,
            VerificationEmailSender verificationEmailSender,
            IntegrationEventPublisher integrationEventPublisher,
            TimeProvider timeProvider,
            IdentityApiMapper identityApiMapper,
            Duration verificationTokenTtl
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "User repository must not be null");
        this.emailVerificationRepository = Objects.requireNonNull(
                emailVerificationRepository,
                "Email verification repository must not be null"
        );
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "Password hasher must not be null");
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
        this.identityApiMapper = Objects.requireNonNull(identityApiMapper, "Identity API mapper must not be null");
        this.verificationTokenTtl = requirePositiveDuration(verificationTokenTtl);
    }

    @Override
    @Transactional
    public RegistrationResult register(RegisterUserCommand command) {
        validateCommand(command);

        Email email = createEmail(command.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException();
        }

        Instant registeredAt = timeProvider.now();
        Instant verificationExpiresAt = registeredAt.plus(verificationTokenTtl);
        PasswordHash passwordHash = passwordHasher.hash(command.rawPassword());

        UUID userId = UUID.randomUUID();
        User user = createUser(
                userId,
                email,
                passwordHash,
                command.firstName(),
                command.lastName(),
                toDomainRoles(command.roles()),
                registeredAt
        );

        String rawVerificationToken = verificationTokenGenerator.generate();
        VerificationTokenHash verificationTokenHash = verificationTokenHasher.hash(rawVerificationToken);
        EmailVerification verification = EmailVerification.create(
                UUID.randomUUID(),
                userId,
                email,
                verificationTokenHash,
                VerificationPurpose.REGISTRATION,
                registeredAt,
                verificationExpiresAt
        );

        UserRegisteredDomainEvent domainEvent = pullRegistrationEvent(user);

        userRepository.save(user);
        emailVerificationRepository.save(verification);

        verificationEmailSender.sendVerificationEmail(
                email,
                rawVerificationToken,
                VerificationPurpose.REGISTRATION,
                verificationExpiresAt
        );

        UserRegisteredEvent integrationEvent = identityApiMapper.toIntegrationEvent(domainEvent);
        integrationEventPublisher.publish(integrationEvent);

        return new RegistrationResult(email.value(), verificationExpiresAt);
    }

    private static void validateCommand(RegisterUserCommand command) {
        if (command == null) {
            throw new InvalidUseCaseInputException("Registration command must not be null");
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
        if (command.roles() == null || command.roles().isEmpty()) {
            throw new InvalidUseCaseInputException("At least one registration role must be provided");
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

    private static User createUser(
            UUID userId,
            Email email,
            PasswordHash passwordHash,
            String firstName,
            String lastName,
            Set<UserRole> roles,
            Instant registeredAt
    ) {
        try {
            return User.register(
                    userId,
                    email,
                    passwordHash,
                    firstName,
                    lastName,
                    roles,
                    registeredAt
            );
        } catch (InvalidUserDataException exception) {
            throw new InvalidUseCaseInputException(exception.getMessage(), exception);
        }
    }

    private static Set<UserRole> toDomainRoles(Set<RegistrationRole> roles) {
        if (roles == null) {
            return null;
        }
        return roles.stream()
                .map(RegisterUserService::toDomainRole)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static UserRole toDomainRole(RegistrationRole role) {
        return switch (role) {
            case TEACHER -> UserRole.TEACHER;
            case STUDENT -> UserRole.STUDENT;
        };
    }

    private static UserRegisteredDomainEvent pullRegistrationEvent(User user) {
        List<UserDomainEvent> domainEvents = user.pullDomainEvents();

        if (domainEvents.size() != 1 || !(domainEvents.getFirst() instanceof UserRegisteredDomainEvent event)) {
            throw new IllegalStateException("A newly registered user must contain one registration event");
        }
        return event;
    }

    private static Duration requirePositiveDuration(Duration duration) {
        Objects.requireNonNull(duration, "Verification token TTL must not be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("Verification token TTL must be positive");
        }
        return duration;
    }
}
