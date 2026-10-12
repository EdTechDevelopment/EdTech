package io.github.edtechdevelopment.tutoring.application.profile.model.query;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PublicProfileSearchCandidate(UUID userId, Instant createdAt) {

    public PublicProfileSearchCandidate {
        Objects.requireNonNull(userId, "User id must not be null");
        Objects.requireNonNull(createdAt, "Profile creation time must not be null");
    }
}
