package io.github.edtechdevelopment.notifications.application.port.out.persistence;

import io.github.edtechdevelopment.notifications.domain.delivery.model.VerificationEmailDelivery;

public interface VerificationEmailDeliveryRepository {

    void save(VerificationEmailDelivery delivery);
}
