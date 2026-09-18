package io.github.edtechdevelopment.identity.infrastructure.configuration;

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
import io.github.edtechdevelopment.identity.application.service.account.RegisterUserService;
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
}
