package io.github.edtechdevelopment.identity.application.service.account;

import io.github.edtechdevelopment.identity.api.event.UserRegisteredEvent;
import io.github.edtechdevelopment.identity.application.command.account.RegistrationRole;
import io.github.edtechdevelopment.identity.application.command.account.RegisterUserCommand;
import io.github.edtechdevelopment.identity.application.exception.EmailAlreadyExistsException;
import io.github.edtechdevelopment.identity.application.exception.InvalidUseCaseInputException;
import io.github.edtechdevelopment.identity.application.mapper.IdentityApiMapper;
import io.github.edtechdevelopment.identity.application.port.out.TimeProvider;
import io.github.edtechdevelopment.identity.application.port.out.messaging.IntegrationEventPublisher;
import io.github.edtechdevelopment.identity.application.port.out.messaging.VerificationEmailSender;
import io.github.edtechdevelopment.identity.application.port.out.persistence.EmailVerificationRepository;
import io.github.edtechdevelopment.identity.application.port.out.persistence.UserRepository;
import io.github.edtechdevelopment.identity.application.port.out.security.PasswordHasher;
import io.github.edtechdevelopment.identity.application.port.out.security.VerificationTokenGenerator;
import io.github.edtechdevelopment.identity.application.port.out.security.VerificationTokenHasher;
import io.github.edtechdevelopment.identity.application.result.RegistrationResult;
import io.github.edtechdevelopment.identity.domain.user.model.Email;
import io.github.edtechdevelopment.identity.domain.user.model.PasswordHash;
import io.github.edtechdevelopment.identity.domain.user.model.User;
import io.github.edtechdevelopment.identity.domain.user.model.UserRole;
import io.github.edtechdevelopment.identity.domain.user.model.UserStatus;
import io.github.edtechdevelopment.identity.domain.verification.model.EmailVerification;
import io.github.edtechdevelopment.identity.domain.verification.model.VerificationPurpose;
import io.github.edtechdevelopment.identity.domain.verification.model.VerificationTokenHash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class RegisterUserServiceTest {

    private static final Instant REGISTERED_AT = Instant.parse("2026-09-14T10:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-09-14T10:05:00Z");
    private static final Duration VERIFICATION_TOKEN_TTL = Duration.ofMinutes(5);
    private static final String RAW_PASSWORD = "Strong!42";
    private static final String RAW_VERIFICATION_TOKEN = "raw-verification-token";
    private static final PasswordHash PASSWORD_HASH = new PasswordHash("stored-password-hash");
    private static final VerificationTokenHash VERIFICATION_TOKEN_HASH =
            new VerificationTokenHash("a".repeat(64));

    private UserRepository userRepository;
    private EmailVerificationRepository emailVerificationRepository;
    private PasswordHasher passwordHasher;
    private VerificationTokenGenerator verificationTokenGenerator;
    private VerificationTokenHasher verificationTokenHasher;
    private VerificationEmailSender verificationEmailSender;
    private IntegrationEventPublisher integrationEventPublisher;
    private TimeProvider timeProvider;
    private RegisterUserService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        emailVerificationRepository = mock(EmailVerificationRepository.class);
        passwordHasher = mock(PasswordHasher.class);
        verificationTokenGenerator = mock(VerificationTokenGenerator.class);
        verificationTokenHasher = mock(VerificationTokenHasher.class);
        verificationEmailSender = mock(VerificationEmailSender.class);
        integrationEventPublisher = mock(IntegrationEventPublisher.class);
        timeProvider = mock(TimeProvider.class);

        service = createService(VERIFICATION_TOKEN_TTL);
    }

    @Test
    void registersUserAndCreatesEmailVerification() {
        when(timeProvider.now()).thenReturn(REGISTERED_AT);
        when(passwordHasher.hash(RAW_PASSWORD)).thenReturn(PASSWORD_HASH);
        when(verificationTokenGenerator.generate()).thenReturn(RAW_VERIFICATION_TOKEN);
        when(verificationTokenHasher.hash(RAW_VERIFICATION_TOKEN)).thenReturn(VERIFICATION_TOKEN_HASH);

        RegistrationResult result = service.register(registrationCommand("  Anna@Example.com  ", RAW_PASSWORD));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertNotNull(savedUser.id());
        assertEquals(new Email("anna@example.com"), savedUser.email());
        assertEquals(PASSWORD_HASH, savedUser.passwordHash());
        assertEquals("Anna", savedUser.firstName());
        assertEquals("Petrova", savedUser.lastName());
        assertEquals(Set.of(UserRole.STUDENT), savedUser.roles());
        assertEquals(UserStatus.PENDING_EMAIL_VERIFICATION, savedUser.status());
        assertEquals(REGISTERED_AT, savedUser.createdAt());
        assertEquals(REGISTERED_AT, savedUser.updatedAt());
        assertTrue(savedUser.emailVerifiedAt().isEmpty());
        assertTrue(savedUser.pullDomainEvents().isEmpty());

        ArgumentCaptor<EmailVerification> verificationCaptor = ArgumentCaptor.forClass(EmailVerification.class);
        verify(emailVerificationRepository).save(verificationCaptor.capture());
        EmailVerification savedVerification = verificationCaptor.getValue();

        assertNotNull(savedVerification.id());
        assertEquals(savedUser.id(), savedVerification.userId());
        assertEquals(savedUser.email(), savedVerification.targetEmail());
        assertEquals(VERIFICATION_TOKEN_HASH, savedVerification.tokenHash());
        assertEquals(VerificationPurpose.REGISTRATION, savedVerification.purpose());
        assertEquals(REGISTERED_AT, savedVerification.createdAt());
        assertEquals(EXPIRES_AT, savedVerification.expiresAt());
        assertTrue(savedVerification.consumedAt().isEmpty());
        assertTrue(savedVerification.invalidatedAt().isEmpty());

        verify(verificationEmailSender).sendVerificationEmail(
                savedUser.email(),
                RAW_VERIFICATION_TOKEN,
                VerificationPurpose.REGISTRATION,
                EXPIRES_AT
        );

        ArgumentCaptor<UserRegisteredEvent> eventCaptor = ArgumentCaptor.forClass(UserRegisteredEvent.class);
        verify(integrationEventPublisher).publish(eventCaptor.capture());
        UserRegisteredEvent publishedEvent = eventCaptor.getValue();

        assertNotNull(publishedEvent.eventId());
        assertEquals(savedUser.id(), publishedEvent.userId());
        assertEquals("anna@example.com", publishedEvent.email());
        assertEquals(REGISTERED_AT, publishedEvent.occurredAt());

        assertEquals("anna@example.com", result.email());
        assertEquals(EXPIRES_AT, result.verificationExpiresAt());
    }

    @Test
    void rejectsAlreadyReservedEmailBeforeCreatingUser() {
        Email normalizedEmail = new Email("anna@example.com");
        when(userRepository.existsByEmail(normalizedEmail)).thenReturn(true);

        assertThrows(
                EmailAlreadyExistsException.class,
                () -> service.register(registrationCommand("Anna@Example.com", RAW_PASSWORD))
        );

        verify(userRepository).existsByEmail(normalizedEmail);
        verifyNoMoreInteractions(userRepository);
        verifyNoInteractions(
                emailVerificationRepository,
                passwordHasher,
                verificationTokenGenerator,
                verificationTokenHasher,
                verificationEmailSender,
                integrationEventPublisher,
                timeProvider
        );
    }

    @Test
    void rejectsPasswordsContainingForbiddenCharacters() {
        assertThrows(
                InvalidUseCaseInputException.class,
                () -> service.register(registrationCommand("anna@example.com", "Password 42"))
        );
        assertThrows(
                InvalidUseCaseInputException.class,
                () -> service.register(registrationCommand("anna@example.com", "Пароль123!"))
        );
        assertThrows(
                InvalidUseCaseInputException.class,
                () -> service.register(registrationCommand("anna@example.com", "Password\t42"))
        );

        verifyNoInteractions(
                userRepository,
                emailVerificationRepository,
                passwordHasher,
                verificationTokenGenerator,
                verificationTokenHasher,
                verificationEmailSender,
                integrationEventPublisher,
                timeProvider
        );
    }

    @Test
    void rejectsPasswordOutsideLengthLimits() {
        assertThrows(
                InvalidUseCaseInputException.class,
                () -> service.register(registrationCommand("anna@example.com", "Short!"))
        );
        assertThrows(
                InvalidUseCaseInputException.class,
                () -> service.register(registrationCommand("anna@example.com", "a".repeat(73)))
        );
    }

    @Test
    void requiresPositiveVerificationTokenTtl() {
        assertThrows(IllegalArgumentException.class, () -> createService(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> createService(Duration.ofSeconds(-1)));
    }

    private RegisterUserService createService(Duration verificationTokenTtl) {
        return new RegisterUserService(
                userRepository,
                emailVerificationRepository,
                passwordHasher,
                verificationTokenGenerator,
                verificationTokenHasher,
                verificationEmailSender,
                integrationEventPublisher,
                timeProvider,
                new IdentityApiMapper(),
                verificationTokenTtl
        );
    }

    private static RegisterUserCommand registrationCommand(String email, String password) {
        return new RegisterUserCommand(
                email,
                password,
                "Anna",
                "Petrova",
                Set.of(RegistrationRole.STUDENT)
        );
    }
}
