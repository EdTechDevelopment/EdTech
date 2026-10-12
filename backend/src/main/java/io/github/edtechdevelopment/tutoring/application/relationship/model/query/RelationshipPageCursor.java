package io.github.edtechdevelopment.tutoring.application.relationship.model.query;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RelationshipPageCursor(Instant createdAt, UUID otherUserId) {

    public RelationshipPageCursor {
        Objects.requireNonNull(createdAt, "Relationship creation time must not be null");
        Objects.requireNonNull(otherUserId, "Other user id must not be null");
    }
}
