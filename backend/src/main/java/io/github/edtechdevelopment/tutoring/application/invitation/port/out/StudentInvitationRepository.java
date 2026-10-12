package io.github.edtechdevelopment.tutoring.application.invitation.port.out;

import io.github.edtechdevelopment.tutoring.application.invitation.model.query.InvitationPageCursor;
import io.github.edtechdevelopment.tutoring.application.invitation.model.query.InvitationPageRow;
import io.github.edtechdevelopment.tutoring.domain.invitation.model.InvitationEmail;
import io.github.edtechdevelopment.tutoring.domain.invitation.model.StudentInvitation;
import io.github.edtechdevelopment.tutoring.domain.invitation.model.InvitationStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentInvitationRepository {

    Optional<StudentInvitation> findByIdForUpdate(UUID invitationId);

    List<StudentInvitation> findPendingByTeacherAndEmailForUpdate(UUID teacherUserId, InvitationEmail email);

    List<StudentInvitation> findPendingByEmailForUpdate(InvitationEmail email, Instant now);

    List<InvitationPageRow> findSentPage(
            UUID teacherUserId, InvitationStatus status, Instant asOf, InvitationPageCursor after, int fetchLimit);

    List<InvitationPageRow> findIncomingPage(
            UUID studentUserId, InvitationStatus status, Instant asOf, InvitationPageCursor after, int fetchLimit);

    void insert(StudentInvitation invitation);

    void update(StudentInvitation invitation);

    void updateAll(List<StudentInvitation> invitations);
}
