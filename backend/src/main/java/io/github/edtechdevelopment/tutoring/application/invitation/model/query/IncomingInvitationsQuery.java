package io.github.edtechdevelopment.tutoring.application.invitation.model.query;

import io.github.edtechdevelopment.tutoring.domain.invitation.model.InvitationStatus;

import java.util.Objects;
import java.util.UUID;

public record IncomingInvitationsQuery(
        UUID studentUserId,
        InvitationStatus status,
        String cursor,
        int limit
) {

    public IncomingInvitationsQuery {
        Objects.requireNonNull(studentUserId, "Student user id must not be null");
        SentInvitationsQuery.validatePage(cursor, limit);
    }

    public static IncomingInvitationsQuery fromStatus(UUID studentUserId, String status, String cursor, int limit) {
        return new IncomingInvitationsQuery(studentUserId, SentInvitationsQuery.parseStatus(status), cursor, limit);
    }
}
