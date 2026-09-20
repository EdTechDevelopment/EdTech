package io.github.edtechdevelopment.identity.domain.user.event;

import java.time.Instant;

public interface UserDomainEvent {
    Instant occurredAt();
}
