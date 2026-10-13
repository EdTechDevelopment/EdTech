package io.github.edtechdevelopment.notifications.application.service;

import io.github.edtechdevelopment.notifications.application.exception.PermanentEmailDeliveryException;
import io.github.edtechdevelopment.notifications.application.exception.TemporaryEmailDeliveryException;
import io.github.edtechdevelopment.notifications.application.port.out.email.InvitationEmailSender;
import io.github.edtechdevelopment.notifications.application.port.out.persistence.InvitationEmailDeliveryRepository;
import io.github.edtechdevelopment.notifications.domain.delivery.model.DeliveryStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Duration;

public class ProcessInvitationEmailDeliveriesService {
    private static final Logger LOG = LoggerFactory.getLogger(ProcessInvitationEmailDeliveriesService.class);
    private final InvitationEmailDeliveryRepository repository;
    private final InvitationEmailSender sender;
    private final TransactionOperations transactions;
    private final Clock clock;
    private final int batchSize;
    private final Duration processingTimeout;

    public ProcessInvitationEmailDeliveriesService(InvitationEmailDeliveryRepository repository, InvitationEmailSender sender,
                                                  TransactionOperations transactions, Clock clock, int batchSize, Duration processingTimeout) {
        this.repository = repository;
        this.sender = sender;
        this.transactions = transactions;
        this.clock = clock;
        this.batchSize = batchSize;
        this.processingTimeout = processingTimeout;
    }

    public void processPending() {
        var now = clock.instant();
        var deliveries = transactions.execute(status -> repository.claimPending(now, now.minus(processingTimeout), batchSize));
        if (deliveries == null) {
            throw new IllegalStateException("Claimed deliveries must not be null");
        }
        for (var claimed : deliveries) {
            var delivery = claimed.delivery();
            DeliveryStatus completion = DeliveryStatus.SENT;
            try {
                if (!clock.instant().isBefore(delivery.expiresAt())) {
                    completion = DeliveryStatus.EXPIRED;
                }
                else {
                    sender.send(delivery);
                }
            } catch (TemporaryEmailDeliveryException exception) {
                completion = DeliveryStatus.PENDING;
            }
            catch (PermanentEmailDeliveryException exception) {
                completion = DeliveryStatus.FAILED;
            }
            catch (RuntimeException exception) {
                completion = DeliveryStatus.PENDING;
                LOG.warn("Invitation email delivery will be retried, deliveryId={}", delivery.id());
            }
            DeliveryStatus result = completion;
            transactions.executeWithoutResult(status ->
                    repository.complete(delivery.id(), claimed.claimId(), result, clock.instant()));
        }
    }
}
