package io.github.edtechdevelopment.notifications.infrastructure.persistence.adapter;

import io.github.edtechdevelopment.notifications.application.port.out.persistence.VerificationEmailDeliveryRepository;
import io.github.edtechdevelopment.notifications.domain.delivery.model.VerificationEmailDelivery;
import io.github.edtechdevelopment.notifications.infrastructure.persistence.data.generated.tables.records.NotificationEmailDeliveriesRecord;
import io.github.edtechdevelopment.notifications.infrastructure.persistence.data.repository.VerificationEmailDeliveryJooqRepository;
import io.github.edtechdevelopment.notifications.infrastructure.persistence.mapper.VerificationEmailDeliveryPersistenceMapper;
import org.springframework.stereotype.Repository;

import java.util.Objects;

@Repository
public class JooqVerificationEmailDeliveryRepositoryAdapter implements VerificationEmailDeliveryRepository {

    private final VerificationEmailDeliveryJooqRepository repository;
    private final VerificationEmailDeliveryPersistenceMapper mapper;

    public JooqVerificationEmailDeliveryRepositoryAdapter(
            VerificationEmailDeliveryJooqRepository repository,
            VerificationEmailDeliveryPersistenceMapper mapper
    ) {
        this.repository = Objects.requireNonNull(
                repository,
                "Verification email delivery jOOQ repository must not be null"
        );
        this.mapper = Objects.requireNonNull(
                mapper,
                "Verification email delivery persistence mapper must not be null"
        );
    }

    @Override
    public void save(VerificationEmailDelivery delivery) {
        Objects.requireNonNull(delivery, "Verification email delivery must not be null");
        NotificationEmailDeliveriesRecord record = mapper.toPersistence(delivery);
        repository.save(record);
    }
}
