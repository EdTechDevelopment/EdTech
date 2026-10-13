package io.github.edtechdevelopment.tutoring.application.profile.model.query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record PublicProfilePageCursor(Instant createdAt, UUID userId, LocalDate ageAsOf) {

    public PublicProfilePageCursor {
        Objects.requireNonNull(createdAt, "Profile creation time must not be null");
        Objects.requireNonNull(userId, "User id must not be null");
        Objects.requireNonNull(ageAsOf, "Age calculation date must not be null");
    }
}
