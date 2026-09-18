package io.github.edtechdevelopment.notifications.application.service;

import io.github.edtechdevelopment.notifications.api.NotificationGateway;
import io.github.edtechdevelopment.notifications.api.command.SendVerificationEmailCommand;
import io.github.edtechdevelopment.notifications.api.model.VerificationEmailPurpose;
import io.github.edtechdevelopment.notifications.application.port.out.TimeProvider;
import io.github.edtechdevelopment.notifications.application.port.out.persistence.VerificationEmailDeliveryRepository;
import io.github.edtechdevelopment.notifications.domain.delivery.model.VerificationEmailDelivery;
import io.github.edtechdevelopment.notifications.domain.delivery.model.VerificationEmailDeliveryPurpose;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class EnqueueVerificationEmailService implements NotificationGateway {

    private final VerificationEmailDeliveryRepository deliveryRepository;
    private final TimeProvider timeProvider;

    public EnqueueVerificationEmailService(
            VerificationEmailDeliveryRepository deliveryRepository,
            TimeProvider timeProvider
    ) {
        this.deliveryRepository = Objects.requireNonNull(
                deliveryRepository,
                "Verification email delivery repository must not be null"
        );
        this.timeProvider = Objects.requireNonNull(timeProvider, "Time provider must not be null");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(SendVerificationEmailCommand command) {
        Objects.requireNonNull(command, "Send verification email command must not be null");

        Instant createdAt = timeProvider.now();
        VerificationEmailDelivery delivery = VerificationEmailDelivery.createPending(
                UUID.randomUUID(),
                command.recipientEmail(),
                command.confirmationUrl(),
                toDeliveryPurpose(command.purpose()),
                createdAt,
                command.expiresAt()
        );

        deliveryRepository.save(delivery);
    }

    private static VerificationEmailDeliveryPurpose toDeliveryPurpose(VerificationEmailPurpose purpose) {
        return switch (purpose) {
            case REGISTRATION -> VerificationEmailDeliveryPurpose.REGISTRATION;
            case EMAIL_CHANGE -> VerificationEmailDeliveryPurpose.EMAIL_CHANGE;
        };
    }
}
