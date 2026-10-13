package io.github.edtechdevelopment.notifications.infrastructure.configuration;

import io.github.edtechdevelopment.notifications.api.InvitationNotificationGateway;
import io.github.edtechdevelopment.notifications.application.port.out.email.InvitationEmailSender;
import io.github.edtechdevelopment.notifications.application.port.out.email.InvitationLinkFactory;
import io.github.edtechdevelopment.notifications.application.port.out.persistence.InvitationEmailDeliveryRepository;
import io.github.edtechdevelopment.notifications.application.service.EnqueueStudentInvitationService;
import io.github.edtechdevelopment.notifications.application.service.ProcessInvitationEmailDeliveriesService;
import io.github.edtechdevelopment.notifications.infrastructure.messaging.email.ConfiguredInvitationLinkFactory;
import io.github.edtechdevelopment.notifications.infrastructure.messaging.email.SpringMailInvitationEmailSender;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InvitationMailProperties.class)
public class InvitationNotificationsConfiguration {
    @Bean
    InvitationLinkFactory invitationLinkFactory(InvitationMailProperties properties) {
        return new ConfiguredInvitationLinkFactory(properties);
    }

    @Bean
    InvitationEmailSender invitationEmailSender(JavaMailSender mail, NotificationMailProperties properties) {
        return new SpringMailInvitationEmailSender(mail, properties);
    }

    @Bean
    InvitationNotificationGateway invitationNotificationGateway(InvitationEmailDeliveryRepository repository,
                                                                 InvitationLinkFactory links, Clock clock) {
        return new EnqueueStudentInvitationService(repository, links, clock);
    }

    @Bean
    ProcessInvitationEmailDeliveriesService processInvitationEmailDeliveriesService(InvitationEmailDeliveryRepository repository,
            InvitationEmailSender sender, PlatformTransactionManager transactionManager, Clock clock, NotificationDeliveryProperties properties) {
        return new ProcessInvitationEmailDeliveriesService(repository, sender, new TransactionTemplate(transactionManager),
                clock, properties.batchSize(), properties.processingTimeout());
    }
}
