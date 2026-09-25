package io.github.edtechdevelopment.identity.application.service.verification;

import io.github.edtechdevelopment.identity.application.command.verification.ResendEmailVerificationCommand;
import io.github.edtechdevelopment.identity.application.exception.InvalidUseCaseInputException;
import io.github.edtechdevelopment.identity.application.port.in.verification.ResendEmailVerificationUseCase;
import io.github.edtechdevelopment.identity.application.port.out.TimeProvider;
import io.github.edtechdevelopment.identity.application.port.out.messaging.VerificationEmailSender;
import io.github.edtechdevelopment.identity.application.port.out.persistence.EmailVerificationRepository;
import io.github.edtechdevelopment.identity.application.port.out.persistence.UserRepository;
import io.github.edtechdevelopment.identity.application.port.out.security.VerificationTokenGenerator;
import io.github.edtechdevelopment.identity.application.port.out.security.VerificationTokenHasher;
import io.github.edtechdevelopment.identity.application.result.ResendVerificationResult;
import io.github.edtechdevelopment.identity.domain.user.exception.InvalidEmailException;
import io.github.edtechdevelopment.identity.domain.user.model.Email;
import io.github.edtechdevelopment.identity.domain.user.model.User;
import io.github.edtechdevelopment.identity.domain.user.model.UserStatus;
import io.github.edtechdevelopment.identity.domain.verification.model.EmailVerification;
import io.github.edtechdevelopment.identity.domain.verification.model.VerificationPurpose;
import io.github.edtechdevelopment.identity.domain.verification.model.VerificationTokenHash;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class ResendEmailVerificationService implements ResendEmailVerificationUseCase {

    private final UserRepository userRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final VerificationTokenGenerator verificationTokenGenerator;
    private final VerificationTokenHasher verificationTokenHasher;
    private final VerificationEmailSender verificationEmailSender;
    private final TimeProvider timeProvider;
    private final Duration verificationTokenTtl;

    public ResendEmailVerificationService(
            UserRepository userRepository,
            EmailVerificationRepository emailVerificationRepository,
            VerificationTokenGenerator verificationTokenGenerator,
            VerificationTokenHasher verificationTokenHasher,
            VerificationEmailSender verificationEmailSender,
            TimeProvider timeProvider,
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
        this.timeProvider = Objects.requireNonNull(timeProvider, "Time provider must not be null");
        this.verificationTokenTtl = requirePositiveDuration(verificationTokenTtl);
    }

    @Override
    @Transactional
    public ResendVerificationResult resendEmailVerification(ResendEmailVerificationCommand command) {
        Email requestedEmail = createEmail(command);
        Instant requestedAt = timeProvider.now();
        Instant verificationExpiresAt = requestedAt.plus(verificationTokenTtl);
        ResendVerificationResult neutralResult = new ResendVerificationResult(
                requestedEmail.value(),
                verificationExpiresAt
        );

        User initialUser = userRepository.findByCurrentOrPendingEmail(requestedEmail).orElse(null);
        if (initialUser == null) {
            return neutralResult;
        }

        User lockedUser = userRepository.findByIdForUpdate(initialUser.id()).orElse(null);
        if (lockedUser == null) {
            return neutralResult;
        }

        VerificationPurpose purpose = determinePurpose(lockedUser, requestedEmail).orElse(null);
        if (purpose == null) {
            return neutralResult;
        }

        emailVerificationRepository.invalidateActiveForUser(
                lockedUser.id(),
                purpose,
                requestedAt
        );

        String rawToken = verificationTokenGenerator.generate();
        VerificationTokenHash tokenHash = verificationTokenHasher.hash(rawToken);
        EmailVerification verification = EmailVerification.create(
                UUID.randomUUID(),
                lockedUser.id(),
                requestedEmail,
                tokenHash,
                purpose,
                requestedAt,
                verificationExpiresAt
        );

        emailVerificationRepository.save(verification);
        verificationEmailSender.sendVerificationEmail(
                requestedEmail,
                rawToken,
                purpose,
                verificationExpiresAt
        );

        return neutralResult;
    }

    private static Optional<VerificationPurpose> determinePurpose(User user, Email requestedEmail) {
        if (user.status() == UserStatus.PENDING_EMAIL_VERIFICATION && user.email().equals(requestedEmail)) {
            return Optional.of(VerificationPurpose.REGISTRATION);
        }

        boolean matchesPendingEmail = user.pendingEmail()
                .filter(requestedEmail::equals)
                .isPresent();
        if (user.status() == UserStatus.ACTIVE && matchesPendingEmail) {
            return Optional.of(VerificationPurpose.EMAIL_CHANGE);
        }

        return Optional.empty();
    }

    private static Email createEmail(ResendEmailVerificationCommand command) {
        if (command == null) {
            throw new InvalidUseCaseInputException("Resend email verification command must not be null");
        }

        try {
            return new Email(command.email());
        } catch (InvalidEmailException exception) {
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
