package io.github.edtechdevelopment.notifications.infrastructure.scheduling;

import io.github.edtechdevelopment.notifications.application.service.ProcessVerificationEmailDeliveriesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Objects;

public final class VerificationEmailDeliveryScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(VerificationEmailDeliveryScheduler.class);
    private final ProcessVerificationEmailDeliveriesService processingService;

    public VerificationEmailDeliveryScheduler(
            ProcessVerificationEmailDeliveriesService processingService
    ) {
        this.processingService = Objects.requireNonNull(
                processingService,
                "Verification email delivery processing service must not be null"
        );
    }

    @Scheduled(
            fixedDelayString = "${notifications.delivery.poll-delay}",
            initialDelayString = "${notifications.delivery.initial-delay}"
    )
    public void processNextBatch() {
        try {
            processingService.processBatch();
        } catch (RuntimeException exception) {
            LOGGER.error("Verification email delivery batch failed", exception);
        }
    }
}
