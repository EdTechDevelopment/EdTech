package io.github.edtechdevelopment.tutoring.infrastructure.invitation.persistence;

import io.github.edtechdevelopment.tutoring.domain.invitation.model.InvitationEmail;
import io.github.edtechdevelopment.tutoring.domain.invitation.model.InvitationStatus;
import io.github.edtechdevelopment.tutoring.domain.invitation.model.StudentInvitation;
import io.github.edtechdevelopment.tutoring.infrastructure.persistence.InvalidPersistenceDataException;
import io.github.edtechdevelopment.tutoring.infrastructure.persistence.data.generated.tables.records.TutoringStudentInvitationsRecord;

import org.springframework.stereotype.Component;

import java.time.ZoneOffset;

@Component
public class StudentInvitationPersistenceMapper {
    public StudentInvitation toDomain(TutoringStudentInvitationsRecord row) {
        try {
            return StudentInvitation.reconstitute(row.getId(), row.getTeacherUserId(),
                    new InvitationEmail(row.getStudentEmail()), row.getStudentUserId(),
                    row.getAttachedAt() == null ? null : row.getAttachedAt().toInstant(),
                    InvitationStatus.valueOf(row.getStatus()), row.getCreatedAt().toInstant(),
                    row.getExpiresAt().toInstant(), row.getRespondedAt() == null ? null : row.getRespondedAt().toInstant());
        } catch (RuntimeException exception) {
            throw new InvalidPersistenceDataException("Invitation contains invalid stored data", exception);
        }
    }

    public TutoringStudentInvitationsRecord toPersistence(StudentInvitation invitation) {
        var row = new TutoringStudentInvitationsRecord();
        row.setId(invitation.id());
        row.setTeacherUserId(invitation.teacherUserId());
        row.setStudentEmail(invitation.studentEmail().value());
        row.setStudentUserId(invitation.studentUserId());
        row.setAttachedAt(invitation.attachedAt() == null ? null : invitation.attachedAt().atOffset(ZoneOffset.UTC));
        row.setStatus(invitation.status().name());
        row.setCreatedAt(invitation.createdAt().atOffset(ZoneOffset.UTC));
        row.setExpiresAt(invitation.expiresAt().atOffset(ZoneOffset.UTC));
        row.setRespondedAt(invitation.respondedAt() == null ? null : invitation.respondedAt().atOffset(ZoneOffset.UTC));
        return row;
    }
}
