package io.github.edtechdevelopment.tutoring.application.invitation.mapper;

import io.github.edtechdevelopment.tutoring.api.model.profile.summary.TeacherProfileSummary;
import io.github.edtechdevelopment.tutoring.application.invitation.model.query.InvitationPageRow;
import io.github.edtechdevelopment.tutoring.application.invitation.model.result.IncomingInvitationResult;
import io.github.edtechdevelopment.tutoring.application.invitation.model.result.InvitationCreatedResult;
import io.github.edtechdevelopment.tutoring.application.invitation.model.result.SentInvitationResult;
import io.github.edtechdevelopment.tutoring.domain.invitation.model.StudentInvitation;

import java.util.Objects;

public final class InvitationResultMapper {

    public InvitationCreatedResult toCreatedResult(StudentInvitation invitation) {
        Objects.requireNonNull(invitation, "Invitation must not be null");
        return new InvitationCreatedResult(invitation.id(), invitation.expiresAt());
    }

    public SentInvitationResult toSentResult(InvitationPageRow row) {
        StudentInvitation invitation = Objects.requireNonNull(row, "Invitation row must not be null").invitation();
        return new SentInvitationResult(invitation.id(), invitation.studentEmail().value(), row.statusAtAsOf(),
                invitation.createdAt(), invitation.expiresAt(), invitation.respondedAt());
    }

    public IncomingInvitationResult toIncomingResult(InvitationPageRow row, TeacherProfileSummary teacher) {
        StudentInvitation invitation = Objects.requireNonNull(row, "Invitation row must not be null").invitation();
        return new IncomingInvitationResult(invitation.id(), Objects.requireNonNull(teacher, "Teacher summary must not be null"),
                row.statusAtAsOf(), invitation.createdAt(), invitation.expiresAt(), invitation.respondedAt());
    }
}
