package io.github.edtechdevelopment.notifications.application.port.out;

import java.time.Instant;

public interface TimeProvider {

    Instant now();
}
