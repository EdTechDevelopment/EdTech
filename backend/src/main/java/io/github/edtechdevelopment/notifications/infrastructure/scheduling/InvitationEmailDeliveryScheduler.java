package io.github.edtechdevelopment.notifications.infrastructure.scheduling;

import io.github.edtechdevelopment.notifications.application.service.ProcessInvitationEmailDeliveriesService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "notifications.delivery.scheduler-enabled", havingValue = "true")
public class InvitationEmailDeliveryScheduler {
    private final ProcessInvitationEmailDeliveriesService deliveries;
    public InvitationEmailDeliveryScheduler(ProcessInvitationEmailDeliveriesService deliveries) {
        this.deliveries = deliveries;
    }

    @Scheduled(fixedDelayString = "${notifications.delivery.poll-delay}", initialDelayString = "${notifications.delivery.initial-delay}")
    public void processPending() {
        deliveries.processPending();
    }
}
