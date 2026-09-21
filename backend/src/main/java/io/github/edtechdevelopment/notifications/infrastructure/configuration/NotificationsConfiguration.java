package io.github.edtechdevelopment.notifications.infrastructure.configuration;

import io.github.edtechdevelopment.notifications.api.NotificationGateway;
import io.github.edtechdevelopment.notifications.application.port.out.TimeProvider;
import io.github.edtechdevelopment.notifications.application.port.out.email.VerificationEmailSender;
import io.github.edtechdevelopment.notifications.application.port.out.persistence.VerificationEmailDeliveryRepository;
import io.github.edtechdevelopment.notifications.application.service.EnqueueVerificationEmailService;
import io.github.edtechdevelopment.notifications.application.service.ProcessVerificationEmailDeliveriesService;
import io.github.edtechdevelopment.notifications.infrastructure.messaging.email.SpringMailVerificationEmailSender;
import io.github.edtechdevelopment.notifications.infrastructure.scheduling.VerificationEmailDeliveryScheduler;
import io.github.edtechdevelopment.notifications.infrastructure.time.SystemTimeProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class NotificationsConfiguration {

    @Bean
    TimeProvider notificationsTimeProvider(Clock clock) {
        return new SystemTimeProvider(clock);
    }

    @Bean
    NotificationGateway notificationGateway(
            VerificationEmailDeliveryRepository deliveryRepository,
            TimeProvider timeProvider
    ) {
        return new EnqueueVerificationEmailService(deliveryRepository, timeProvider);
    }

    @Bean
    VerificationEmailSender notificationsVerificationEmailSender(
            JavaMailSender mailSender,
            NotificationMailProperties properties
    ) {
        return new SpringMailVerificationEmailSender(mailSender, properties);
    }

    @Bean
    ProcessVerificationEmailDeliveriesService processVerificationEmailDeliveriesService(
            VerificationEmailDeliveryRepository deliveryRepository,
            VerificationEmailSender emailSender,
            TimeProvider timeProvider,
            PlatformTransactionManager transactionManager,
            NotificationDeliveryProperties properties
    ) {
        return new ProcessVerificationEmailDeliveriesService(
                deliveryRepository,
                emailSender,
                timeProvider,
                new TransactionTemplate(transactionManager),
                properties.batchSize(),
                properties.processingTimeout()
        );
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "notifications.delivery",
            name = "scheduler-enabled",
            havingValue = "true"
    )
    VerificationEmailDeliveryScheduler verificationEmailDeliveryScheduler(
            ProcessVerificationEmailDeliveriesService processingService
    ) {
        return new VerificationEmailDeliveryScheduler(processingService);
    }
}
