package io.github.edtechdevelopment.tutoring.application.invitation.model.query;

import io.github.edtechdevelopment.tutoring.domain.invitation.model.InvitationStatus;

import java.util.Objects;
import java.util.UUID;

public record SentInvitationsQuery(
        UUID teacherUserId,
        InvitationStatus status,
        String cursor,
        int limit
) {

    public SentInvitationsQuery {
        Objects.requireNonNull(teacherUserId, "Teacher user id must not be null");
        validatePage(cursor, limit);
    }

    static void validatePage(String cursor, int limit) {
        if (cursor != null && cursor.isBlank()) {
            throw new IllegalArgumentException("Cursor must not be blank");
        }
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Limit must be between 1 and 100");
        }
    }

    public static SentInvitationsQuery fromStatus(UUID teacherUserId, String status, String cursor, int limit) {
        return new SentInvitationsQuery(teacherUserId, parseStatus(status), cursor, limit);
    }

    static InvitationStatus parseStatus(String status) {
        if (status == null) { return null; }
        try { return InvitationStatus.valueOf(status); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Invitation status is invalid"); }
    }
}
