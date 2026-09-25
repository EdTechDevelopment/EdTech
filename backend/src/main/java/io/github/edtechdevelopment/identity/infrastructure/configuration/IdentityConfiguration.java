package io.github.edtechdevelopment.identity.infrastructure.configuration;

import io.github.edtechdevelopment.identity.application.mapper.IdentityApiMapper;
import io.github.edtechdevelopment.identity.application.mapper.UserResultMapper;
import io.github.edtechdevelopment.identity.api.query.IdentityQuery;
import io.github.edtechdevelopment.identity.application.port.in.account.RegisterUserUseCase;
import io.github.edtechdevelopment.identity.application.port.in.account.UpdateCurrentUserUseCase;
import io.github.edtechdevelopment.identity.application.port.in.authentication.LoginUseCase;
import io.github.edtechdevelopment.identity.application.port.in.authentication.LogoutUseCase;
import io.github.edtechdevelopment.identity.application.port.in.authentication.RefreshTokenUseCase;
import io.github.edtechdevelopment.identity.application.port.in.verification.ConfirmEmailUseCase;
import io.github.edtechdevelopment.identity.application.port.in.verification.ResendEmailVerificationUseCase;
import io.github.edtechdevelopment.identity.application.port.out.TimeProvider;
import io.github.edtechdevelopment.identity.application.port.out.messaging.IntegrationEventPublisher;
import io.github.edtechdevelopment.identity.application.port.out.messaging.VerificationEmailSender;
import io.github.edtechdevelopment.identity.application.port.out.persistence.EmailVerificationRepository;
import io.github.edtechdevelopment.identity.application.port.out.persistence.RefreshTokenRepository;
import io.github.edtechdevelopment.identity.application.port.out.persistence.UserRepository;
import io.github.edtechdevelopment.identity.application.port.out.security.AccessTokenIssuer;
import io.github.edtechdevelopment.identity.application.port.out.security.PasswordHasher;
import io.github.edtechdevelopment.identity.application.port.out.security.RefreshTokenHasher;
import io.github.edtechdevelopment.identity.application.port.out.security.RefreshTokenIssuer;
import io.github.edtechdevelopment.identity.application.port.out.security.VerificationTokenGenerator;
import io.github.edtechdevelopment.identity.application.port.out.security.VerificationTokenHasher;
import io.github.edtechdevelopment.identity.application.service.account.RegisterUserService;
import io.github.edtechdevelopment.identity.application.service.account.IdentityQueryService;
import io.github.edtechdevelopment.identity.application.service.account.UpdateCurrentUserService;
import io.github.edtechdevelopment.identity.application.service.authentication.LoginService;
import io.github.edtechdevelopment.identity.application.service.authentication.LogoutService;
import io.github.edtechdevelopment.identity.application.service.authentication.RefreshSessionFactory;
import io.github.edtechdevelopment.identity.application.service.authentication.RefreshTokenService;
import io.github.edtechdevelopment.identity.application.service.verification.ConfirmEmailService;
import io.github.edtechdevelopment.identity.application.service.verification.ResendEmailVerificationService;
import io.github.edtechdevelopment.identity.infrastructure.messaging.email.IdentityNotificationProperties;
import io.github.edtechdevelopment.identity.infrastructure.messaging.email.NotificationVerificationEmailAdapter;
import io.github.edtechdevelopment.identity.infrastructure.security.configuration.IdentityTokenProperties;
import io.github.edtechdevelopment.notifications.api.NotificationGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class IdentityConfiguration {

    @Bean
    IdentityApiMapper identityApiMapper() {
        return new IdentityApiMapper();
    }

    @Bean
    UserResultMapper userResultMapper() {
        return new UserResultMapper();
    }

    @Bean
    IdentityQuery identityQuery(
            UserRepository userRepository,
            IdentityApiMapper identityApiMapper
    ) {
        return new IdentityQueryService(userRepository, identityApiMapper);
    }

    @Bean
    RefreshSessionFactory refreshSessionFactory(
            RefreshTokenIssuer refreshTokenIssuer,
            RefreshTokenHasher refreshTokenHasher,
            IdentityTokenProperties tokenProperties
    ) {
        return new RefreshSessionFactory(
                refreshTokenIssuer,
                refreshTokenHasher,
                tokenProperties.refreshFamilyTtl()
        );
    }

    @Bean
    LoginUseCase loginUseCase(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordHasher passwordHasher,
            AccessTokenIssuer accessTokenIssuer,
            RefreshSessionFactory refreshSessionFactory,
            TimeProvider timeProvider
    ) {
        return new LoginService(
                userRepository,
                refreshTokenRepository,
                passwordHasher,
                accessTokenIssuer,
                refreshSessionFactory,
                timeProvider
        );
    }

    @Bean
    RefreshTokenUseCase refreshTokenUseCase(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            RefreshTokenHasher refreshTokenHasher,
            AccessTokenIssuer accessTokenIssuer,
            RefreshSessionFactory refreshSessionFactory,
            TimeProvider timeProvider
    ) {
        return new RefreshTokenService(
                userRepository,
                refreshTokenRepository,
                refreshTokenHasher,
                accessTokenIssuer,
                refreshSessionFactory,
                timeProvider
        );
    }

    @Bean
    LogoutUseCase logoutUseCase(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            RefreshTokenHasher refreshTokenHasher,
            TimeProvider timeProvider
    ) {
        return new LogoutService(
                userRepository,
                refreshTokenRepository,
                refreshTokenHasher,
                timeProvider
        );
    }

    @Bean
    VerificationEmailSender verificationEmailSender(
            NotificationGateway notificationGateway,
            IdentityNotificationProperties properties
    ) {
        return new NotificationVerificationEmailAdapter(notificationGateway, properties);
    }

    @Bean
    RegisterUserUseCase registerUserUseCase(
            UserRepository userRepository,
            EmailVerificationRepository emailVerificationRepository,
            PasswordHasher passwordHasher,
            VerificationTokenGenerator verificationTokenGenerator,
            VerificationTokenHasher verificationTokenHasher,
            VerificationEmailSender verificationEmailSender,
            IntegrationEventPublisher integrationEventPublisher,
            TimeProvider timeProvider,
            IdentityApiMapper identityApiMapper,
            IdentityTokenProperties tokenProperties
    ) {
        return new RegisterUserService(
                userRepository,
                emailVerificationRepository,
                passwordHasher,
                verificationTokenGenerator,
                verificationTokenHasher,
                verificationEmailSender,
                integrationEventPublisher,
                timeProvider,
                identityApiMapper,
                tokenProperties.verificationTtl()
        );
    }

    @Bean
    ConfirmEmailUseCase confirmEmailUseCase(
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
        return new ConfirmEmailService(
                userRepository,
                emailVerificationRepository,
                refreshTokenRepository,
                verificationTokenHasher,
                accessTokenIssuer,
                refreshSessionFactory,
                integrationEventPublisher,
                timeProvider,
                identityApiMapper
        );
    }

    @Bean
    ResendEmailVerificationUseCase resendEmailVerificationUseCase(
            UserRepository userRepository,
            EmailVerificationRepository emailVerificationRepository,
            VerificationTokenGenerator verificationTokenGenerator,
            VerificationTokenHasher verificationTokenHasher,
            VerificationEmailSender verificationEmailSender,
            TimeProvider timeProvider,
            IdentityTokenProperties tokenProperties
    ) {
        return new ResendEmailVerificationService(
                userRepository,
                emailVerificationRepository,
                verificationTokenGenerator,
                verificationTokenHasher,
                verificationEmailSender,
                timeProvider,
                tokenProperties.verificationTtl()
        );
    }

    @Bean
    UpdateCurrentUserUseCase updateCurrentUserUseCase(
            UserRepository userRepository,
            EmailVerificationRepository emailVerificationRepository,
            VerificationTokenGenerator verificationTokenGenerator,
            VerificationTokenHasher verificationTokenHasher,
            VerificationEmailSender verificationEmailSender,
            IntegrationEventPublisher integrationEventPublisher,
            TimeProvider timeProvider,
            UserResultMapper userResultMapper,
            IdentityApiMapper identityApiMapper,
            IdentityTokenProperties tokenProperties
    ) {
        return new UpdateCurrentUserService(
                userRepository,
                emailVerificationRepository,
                verificationTokenGenerator,
                verificationTokenHasher,
                verificationEmailSender,
                integrationEventPublisher,
                timeProvider,
                userResultMapper,
                identityApiMapper,
                tokenProperties.verificationTtl()
        );
    }
}
