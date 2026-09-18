package io.github.edtechdevelopment.notifications.infrastructure.configuration;

import io.github.edtechdevelopment.notifications.api.NotificationGateway;
import io.github.edtechdevelopment.notifications.application.port.out.TimeProvider;
import io.github.edtechdevelopment.notifications.application.port.out.persistence.VerificationEmailDeliveryRepository;
import io.github.edtechdevelopment.notifications.application.service.EnqueueVerificationEmailService;
import io.github.edtechdevelopment.notifications.infrastructure.time.SystemTimeProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
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
}
