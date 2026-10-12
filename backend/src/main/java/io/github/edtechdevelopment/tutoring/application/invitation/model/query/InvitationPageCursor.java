package io.github.edtechdevelopment.tutoring.application.invitation.model.query;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record InvitationPageCursor(Instant createdAt, UUID invitationId, Instant asOf) {

    public InvitationPageCursor {
        Objects.requireNonNull(createdAt, "Invitation creation time must not be null");
        Objects.requireNonNull(invitationId, "Invitation id must not be null");
        Objects.requireNonNull(asOf, "Snapshot time must not be null");
    }
}
