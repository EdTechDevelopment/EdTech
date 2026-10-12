package io.github.edtechdevelopment.tutoring.application.email.port.out;

public interface NormalizedEmailLock {

    void lock(String normalizedEmail);
}
