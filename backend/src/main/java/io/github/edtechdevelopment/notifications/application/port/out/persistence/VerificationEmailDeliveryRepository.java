package io.github.edtechdevelopment.notifications.application.port.out.persistence;

import io.github.edtechdevelopment.notifications.domain.delivery.model.VerificationEmailDelivery;

import java.time.Instant;
import java.util.List;

public interface VerificationEmailDeliveryRepository {

    void save(VerificationEmailDelivery delivery);

    List<VerificationEmailDelivery> findPending(Instant now, int limit);

    List<VerificationEmailDelivery> findStaleProcessing(
            Instant staleBefore,
            Instant now,
            int limit
    );

    List<VerificationEmailDelivery> findExpired(Instant now, int limit);

    void update(VerificationEmailDelivery delivery);
}
