package io.github.edtechdevelopment.identity.application.port.out;

import java.time.Instant;

public interface TimeProvider {

    Instant now();
}
