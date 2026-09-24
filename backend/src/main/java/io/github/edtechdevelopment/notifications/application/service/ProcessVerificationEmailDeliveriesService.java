package io.github.edtechdevelopment.notifications.application.service;

import io.github.edtechdevelopment.notifications.application.exception.PermanentEmailDeliveryException;
import io.github.edtechdevelopment.notifications.application.exception.TemporaryEmailDeliveryException;
import io.github.edtechdevelopment.notifications.application.port.out.TimeProvider;
import io.github.edtechdevelopment.notifications.application.port.out.email.VerificationEmailMessage;
import io.github.edtechdevelopment.notifications.application.port.out.email.VerificationEmailSender;
import io.github.edtechdevelopment.notifications.application.port.out.persistence.VerificationEmailDeliveryRepository;
import io.github.edtechdevelopment.notifications.domain.delivery.model.VerificationEmailDelivery;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class ProcessVerificationEmailDeliveriesService {

    private final VerificationEmailDeliveryRepository deliveryRepository;
    private final VerificationEmailSender emailSender;
    private final TimeProvider timeProvider;
    private final TransactionOperations transactionOperations;
    private final int batchSize;
    private final Duration processingTimeout;

    public ProcessVerificationEmailDeliveriesService(
            VerificationEmailDeliveryRepository deliveryRepository,
            VerificationEmailSender emailSender,
            TimeProvider timeProvider,
            TransactionOperations transactionOperations,
            int batchSize,
            Duration processingTimeout
    ) {
        this.deliveryRepository = Objects.requireNonNull(
                deliveryRepository,
                "Verification email delivery repository must not be null"
        );
        this.emailSender = Objects.requireNonNull(emailSender, "Verification email sender must not be null");
        this.timeProvider = Objects.requireNonNull(timeProvider, "Time provider must not be null");
        this.transactionOperations = Objects.requireNonNull(
                transactionOperations,
                "Transaction operations must not be null"
        );
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Verification email delivery batch size must be positive");
        }
        this.batchSize = batchSize;
        this.processingTimeout = requirePositiveDuration(processingTimeout);
    }

    public void processBatch() {
        List<VerificationEmailDelivery> claimedDeliveries = Objects.requireNonNull(
                transactionOperations.execute(status -> prepareBatch()),
                "Claimed verification email deliveries must not be null"
        );

        RuntimeException firstUnexpectedFailure = null;
        for (VerificationEmailDelivery delivery : claimedDeliveries) {
            try {
                processDelivery(delivery);
            } catch (RuntimeException exception) {
                if (firstUnexpectedFailure == null) {
                    firstUnexpectedFailure = exception;
                } else {
                    firstUnexpectedFailure.addSuppressed(exception);
                }
            }
        }

        if (firstUnexpectedFailure != null) {
            throw firstUnexpectedFailure;
        }
    }

    private List<VerificationEmailDelivery> prepareBatch() {
        Instant now = timeProvider.now();

        expireDeliveries(now);
        recoverStaleDeliveries(now);

        List<VerificationEmailDelivery> pendingDeliveries = deliveryRepository.findPending(now, batchSize);
        for (VerificationEmailDelivery delivery : pendingDeliveries) {
            delivery.startProcessing(now);
            deliveryRepository.update(delivery);
        }
        return List.copyOf(pendingDeliveries);
    }

    private void expireDeliveries(Instant now) {
        List<VerificationEmailDelivery> expiredDeliveries = deliveryRepository.findExpired(now, batchSize);
        for (VerificationEmailDelivery delivery : expiredDeliveries) {
            delivery.markExpired(now);
            deliveryRepository.update(delivery);
        }
    }

    private void recoverStaleDeliveries(Instant now) {
        Instant staleBefore = now.minus(processingTimeout);
        List<VerificationEmailDelivery> staleDeliveries = deliveryRepository.findStaleProcessing(
                staleBefore,
                now,
                batchSize
        );
        for (VerificationEmailDelivery delivery : staleDeliveries) {
            delivery.returnToPending(now);
            deliveryRepository.update(delivery);
        }
    }

    private void processDelivery(VerificationEmailDelivery delivery) {
        VerificationEmailMessage message = new VerificationEmailMessage(
                delivery.recipientEmail(),
                delivery.confirmationUrl().orElseThrow(
                        () -> new IllegalStateException("A processing delivery must contain a confirmation URL")
                ),
                delivery.purpose()
        );

        DeliveryCompletion completion;
        try {
            emailSender.send(message);
            completion = DeliveryCompletion.SENT;
        } catch (TemporaryEmailDeliveryException exception) {
            completion = DeliveryCompletion.RETRY;
        } catch (PermanentEmailDeliveryException exception) {
            completion = DeliveryCompletion.FAILED;
        }
        completeDelivery(delivery, completion);
    }

    private void completeDelivery(
            VerificationEmailDelivery delivery,
            DeliveryCompletion completion
    ) {
        transactionOperations.executeWithoutResult(status -> {
            Instant completedAt = timeProvider.now();
            if (!completedAt.isBefore(delivery.expiresAt())) {
                delivery.markExpired(completedAt);
            } else {
                switch (completion) {
                    case SENT -> delivery.markSent(completedAt);
                    case RETRY -> delivery.returnToPending(completedAt);
                    case FAILED -> delivery.markFailed(completedAt);
                }
            }
            deliveryRepository.update(delivery);
        });
    }

    private static Duration requirePositiveDuration(Duration duration) {
        Objects.requireNonNull(duration, "Verification email delivery processing timeout must not be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(
                    "Verification email delivery processing timeout must be positive"
            );
        }
        return duration;
    }

    private enum DeliveryCompletion {
        SENT,
        RETRY,
        FAILED
    }
}
