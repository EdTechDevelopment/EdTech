package io.github.edtechdevelopment.tutoring.application.invitation.model.result;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record InvitationAcceptedResult(
        UUID invitationId,
        UUID teacherUserId,
        UUID studentUserId,
        Instant linkedAt
) {

    public InvitationAcceptedResult {
        Objects.requireNonNull(invitationId, "Invitation id must not be null");
        Objects.requireNonNull(teacherUserId, "Teacher user id must not be null");
        Objects.requireNonNull(studentUserId, "Student user id must not be null");
        Objects.requireNonNull(linkedAt, "Link time must not be null");
    }
}
