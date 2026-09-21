package io.github.edtechdevelopment.notifications.infrastructure.scheduling;

import io.github.edtechdevelopment.notifications.application.service.ProcessVerificationEmailDeliveriesService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class VerificationEmailDeliverySchedulerTest {

    @Test
    void delegatesBatchProcessingToApplicationService() {
        ProcessVerificationEmailDeliveriesService processingService =
                mock(ProcessVerificationEmailDeliveriesService.class);
        VerificationEmailDeliveryScheduler scheduler =
                new VerificationEmailDeliveryScheduler(processingService);

        scheduler.processNextBatch();

        verify(processingService).processBatch();
    }

    @Test
    void keepsSchedulerAliveAfterUnexpectedBatchFailure() {
        ProcessVerificationEmailDeliveriesService processingService =
                mock(ProcessVerificationEmailDeliveriesService.class);
        doThrow(new IllegalStateException("Unexpected processing failure"))
                .when(processingService)
                .processBatch();
        VerificationEmailDeliveryScheduler scheduler =
                new VerificationEmailDeliveryScheduler(processingService);

        assertDoesNotThrow(scheduler::processNextBatch);

        verify(processingService).processBatch();
    }
}
