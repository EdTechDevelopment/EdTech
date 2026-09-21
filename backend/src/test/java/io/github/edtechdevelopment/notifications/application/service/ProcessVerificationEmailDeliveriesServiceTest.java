package io.github.edtechdevelopment.notifications.application.service;

import io.github.edtechdevelopment.notifications.application.exception.PermanentEmailDeliveryException;
import io.github.edtechdevelopment.notifications.application.exception.TemporaryEmailDeliveryException;
import io.github.edtechdevelopment.notifications.application.port.out.TimeProvider;
import io.github.edtechdevelopment.notifications.application.port.out.email.VerificationEmailMessage;
import io.github.edtechdevelopment.notifications.application.port.out.email.VerificationEmailSender;
import io.github.edtechdevelopment.notifications.application.port.out.persistence.VerificationEmailDeliveryRepository;
import io.github.edtechdevelopment.notifications.domain.delivery.model.DeliveryStatus;
import io.github.edtechdevelopment.notifications.domain.delivery.model.VerificationEmailDelivery;
import io.github.edtechdevelopment.notifications.domain.delivery.model.VerificationEmailDeliveryPurpose;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessVerificationEmailDeliveriesServiceTest {

    private static final int BATCH_SIZE = 10;
    private static final Duration PROCESSING_TIMEOUT = Duration.ofMinutes(1);
    private static final Instant CREATED_AT = Instant.parse("2026-09-21T10:00:00Z");
    private static final Instant BATCH_STARTED_AT = Instant.parse("2026-09-21T10:01:00Z");
    private static final Instant DELIVERY_COMPLETED_AT = Instant.parse("2026-09-21T10:01:10Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-09-21T10:05:00Z");
    private static final URI CONFIRMATION_URL = URI.create(
            "http://frontend.example:3000/verify-email?token=sensitive-token"
    );
    private static final TransactionOperations DIRECT_TRANSACTIONS = new DirectTransactionOperations();

    @Test
    void marksExpiredDeliveriesBeforeClaimingPendingBatch() {
        TestDependencies dependencies = new TestDependencies();
        VerificationEmailDelivery expiredDelivery = pendingDelivery(BATCH_STARTED_AT);
        when(dependencies.timeProvider.now()).thenReturn(BATCH_STARTED_AT);
        when(dependencies.repository.findExpired(BATCH_STARTED_AT, BATCH_SIZE))
                .thenReturn(List.of(expiredDelivery));
        when(dependencies.repository.findStaleProcessing(
                BATCH_STARTED_AT.minus(PROCESSING_TIMEOUT),
                BATCH_STARTED_AT,
                BATCH_SIZE
        )).thenReturn(List.of());
        when(dependencies.repository.findPending(BATCH_STARTED_AT, BATCH_SIZE)).thenReturn(List.of());

        dependencies.service().processBatch();

        assertAll(
                () -> assertEquals(DeliveryStatus.EXPIRED, expiredDelivery.status()),
                () -> assertTrue(expiredDelivery.confirmationUrl().isEmpty()),
                () -> assertEquals(BATCH_STARTED_AT, expiredDelivery.updatedAt())
        );
        verify(dependencies.repository).update(expiredDelivery);
        verify(dependencies.emailSender, never()).send(any());
    }

    @Test
    void returnsStaleProcessingDeliveriesToPending() {
        TestDependencies dependencies = new TestDependencies();
        Instant oldCreatedAt = BATCH_STARTED_AT.minus(Duration.ofMinutes(5));
        Instant oldProcessingAt = BATCH_STARTED_AT.minus(Duration.ofMinutes(2));
        VerificationEmailDelivery staleDelivery = processingDelivery(
                oldCreatedAt,
                oldProcessingAt,
                EXPIRES_AT
        );
        prepareEmptyExpiredSelection(dependencies);
        when(dependencies.timeProvider.now()).thenReturn(BATCH_STARTED_AT);
        when(dependencies.repository.findStaleProcessing(
                BATCH_STARTED_AT.minus(PROCESSING_TIMEOUT),
                BATCH_STARTED_AT,
                BATCH_SIZE
        )).thenReturn(List.of(staleDelivery));
        when(dependencies.repository.findPending(BATCH_STARTED_AT, BATCH_SIZE)).thenReturn(List.of());

        dependencies.service().processBatch();

        assertAll(
                () -> assertEquals(DeliveryStatus.PENDING, staleDelivery.status()),
                () -> assertEquals(BATCH_STARTED_AT, staleDelivery.updatedAt()),
                () -> assertEquals(CONFIRMATION_URL, staleDelivery.confirmationUrl().orElseThrow())
        );
        verify(dependencies.repository).update(staleDelivery);
        verify(dependencies.emailSender, never()).send(any());
    }

    @Test
    void sendsPendingDeliveryAndMarksItAsSent() {
        TestDependencies dependencies = preparedDependenciesWithPendingDelivery(EXPIRES_AT);
        VerificationEmailDelivery delivery = dependencies.pendingDelivery;

        dependencies.service().processBatch();

        ArgumentCaptor<VerificationEmailMessage> messageCaptor =
                ArgumentCaptor.forClass(VerificationEmailMessage.class);
        verify(dependencies.emailSender).send(messageCaptor.capture());
        VerificationEmailMessage message = messageCaptor.getValue();

        assertAll(
                () -> assertEquals("anna@example.com", message.recipientEmail()),
                () -> assertEquals(CONFIRMATION_URL, message.confirmationUrl()),
                () -> assertEquals(VerificationEmailDeliveryPurpose.REGISTRATION, message.purpose()),
                () -> assertEquals(DeliveryStatus.SENT, delivery.status()),
                () -> assertEquals(DELIVERY_COMPLETED_AT, delivery.sentAt().orElseThrow()),
                () -> assertTrue(delivery.confirmationUrl().isEmpty())
        );
        verify(dependencies.repository, times(2)).update(delivery);
    }

    @Test
    void returnsDeliveryToPendingAfterTemporaryFailure() {
        TestDependencies dependencies = preparedDependenciesWithPendingDelivery(EXPIRES_AT);
        doThrow(new TemporaryEmailDeliveryException("SMTP is temporarily unavailable"))
                .when(dependencies.emailSender)
                .send(any());

        dependencies.service().processBatch();

        assertAll(
                () -> assertEquals(DeliveryStatus.PENDING, dependencies.pendingDelivery.status()),
                () -> assertEquals(DELIVERY_COMPLETED_AT, dependencies.pendingDelivery.updatedAt()),
                () -> assertEquals(
                        CONFIRMATION_URL,
                        dependencies.pendingDelivery.confirmationUrl().orElseThrow()
                )
        );
        verify(dependencies.repository, times(2)).update(dependencies.pendingDelivery);
    }

    @Test
    void marksDeliveryAsFailedAfterPermanentFailure() {
        TestDependencies dependencies = preparedDependenciesWithPendingDelivery(EXPIRES_AT);
        doThrow(new PermanentEmailDeliveryException("Recipient was rejected"))
                .when(dependencies.emailSender)
                .send(any());

        dependencies.service().processBatch();

        assertAll(
                () -> assertEquals(DeliveryStatus.FAILED, dependencies.pendingDelivery.status()),
                () -> assertEquals(DELIVERY_COMPLETED_AT, dependencies.pendingDelivery.updatedAt()),
                () -> assertTrue(dependencies.pendingDelivery.confirmationUrl().isEmpty())
        );
        verify(dependencies.repository, times(2)).update(dependencies.pendingDelivery);
    }

    @Test
    void marksDeliveryAsExpiredWhenItExpiresDuringSending() {
        Instant expiresDuringSending = BATCH_STARTED_AT.plusSeconds(5);
        TestDependencies dependencies = preparedDependenciesWithPendingDelivery(expiresDuringSending);

        dependencies.service().processBatch();

        assertAll(
                () -> assertEquals(DeliveryStatus.EXPIRED, dependencies.pendingDelivery.status()),
                () -> assertEquals(DELIVERY_COMPLETED_AT, dependencies.pendingDelivery.updatedAt()),
                () -> assertTrue(dependencies.pendingDelivery.confirmationUrl().isEmpty()),
                () -> assertTrue(dependencies.pendingDelivery.sentAt().isEmpty())
        );
    }

    @Test
    void propagatesUnexpectedFailureAndLeavesDeliveryProcessingForRecovery() {
        TestDependencies dependencies = preparedDependenciesWithPendingDelivery(EXPIRES_AT);
        IllegalStateException unexpectedFailure = new IllegalStateException("Unexpected sender failure");
        doThrow(unexpectedFailure).when(dependencies.emailSender).send(any());

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> dependencies.service().processBatch()
        );

        assertAll(
                () -> assertSame(unexpectedFailure, thrown),
                () -> assertEquals(DeliveryStatus.PROCESSING, dependencies.pendingDelivery.status()),
                () -> assertEquals(BATCH_STARTED_AT, dependencies.pendingDelivery.updatedAt())
        );
        verify(dependencies.repository).update(dependencies.pendingDelivery);
    }

    @Test
    void rejectsNonPositiveBatchSize() {
        TestDependencies dependencies = new TestDependencies();

        assertThrows(
                IllegalArgumentException.class,
                () -> new ProcessVerificationEmailDeliveriesService(
                        dependencies.repository,
                        dependencies.emailSender,
                        dependencies.timeProvider,
                        DIRECT_TRANSACTIONS,
                        0,
                        PROCESSING_TIMEOUT
                )
        );
    }

    private static TestDependencies preparedDependenciesWithPendingDelivery(Instant expiresAt) {
        TestDependencies dependencies = new TestDependencies();
        VerificationEmailDelivery delivery = pendingDelivery(expiresAt);
        dependencies.pendingDelivery = delivery;

        when(dependencies.timeProvider.now()).thenReturn(BATCH_STARTED_AT, DELIVERY_COMPLETED_AT);
        when(dependencies.repository.findExpired(BATCH_STARTED_AT, BATCH_SIZE)).thenReturn(List.of());
        when(dependencies.repository.findStaleProcessing(
                BATCH_STARTED_AT.minus(PROCESSING_TIMEOUT),
                BATCH_STARTED_AT,
                BATCH_SIZE
        )).thenReturn(List.of());
        when(dependencies.repository.findPending(BATCH_STARTED_AT, BATCH_SIZE)).thenReturn(List.of(delivery));
        return dependencies;
    }

    private static void prepareEmptyExpiredSelection(TestDependencies dependencies) {
        when(dependencies.repository.findExpired(BATCH_STARTED_AT, BATCH_SIZE)).thenReturn(List.of());
    }

    private static VerificationEmailDelivery pendingDelivery(Instant expiresAt) {
        return VerificationEmailDelivery.createPending(
                UUID.fromString("3a453b90-6b4c-4ee7-a384-725588e99280"),
                "anna@example.com",
                CONFIRMATION_URL,
                VerificationEmailDeliveryPurpose.REGISTRATION,
                CREATED_AT,
                expiresAt
        );
    }

    private static VerificationEmailDelivery processingDelivery(
            Instant createdAt,
            Instant processingAt,
            Instant expiresAt
    ) {
        VerificationEmailDelivery delivery = VerificationEmailDelivery.createPending(
                UUID.fromString("5b977320-43f7-4946-ae40-c023453ae6cb"),
                "anna@example.com",
                CONFIRMATION_URL,
                VerificationEmailDeliveryPurpose.REGISTRATION,
                createdAt,
                expiresAt
        );
        delivery.startProcessing(processingAt);
        return delivery;
    }

    private static final class TestDependencies {

        private final VerificationEmailDeliveryRepository repository =
                mock(VerificationEmailDeliveryRepository.class);
        private final VerificationEmailSender emailSender = mock(VerificationEmailSender.class);
        private final TimeProvider timeProvider = mock(TimeProvider.class);
        private VerificationEmailDelivery pendingDelivery;

        private ProcessVerificationEmailDeliveriesService service() {
            return new ProcessVerificationEmailDeliveriesService(
                    repository,
                    emailSender,
                    timeProvider,
                    DIRECT_TRANSACTIONS,
                    BATCH_SIZE,
                    PROCESSING_TIMEOUT
            );
        }
    }

    private static final class DirectTransactionOperations implements TransactionOperations {

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(null);
        }
    }
}
