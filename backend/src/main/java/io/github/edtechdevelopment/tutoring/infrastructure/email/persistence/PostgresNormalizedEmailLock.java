package io.github.edtechdevelopment.tutoring.infrastructure.email.persistence;

import io.github.edtechdevelopment.tutoring.application.email.port.out.NormalizedEmailLock;
import io.github.edtechdevelopment.tutoring.infrastructure.persistence.TransactionKeyLock;

import org.springframework.stereotype.Component;

@Component
public class PostgresNormalizedEmailLock implements NormalizedEmailLock {
    private final TransactionKeyLock lock;
    public PostgresNormalizedEmailLock(TransactionKeyLock lock) {
        this.lock = lock;
    }

    @Override
    public void lock(String normalizedEmail) {
        lock.lock("email", normalizedEmail);
    }
}
