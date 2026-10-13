package io.github.edtechdevelopment.tutoring.infrastructure.persistence;

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
public class TransactionKeyLock {
    private final DSLContext dsl;

    public TransactionKeyLock(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(String namespace, String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest((namespace + ":" + value).getBytes(StandardCharsets.UTF_8));
            long key = ByteBuffer.wrap(bytes).getLong();
            dsl.fetch("select pg_advisory_xact_lock(?)", key);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
