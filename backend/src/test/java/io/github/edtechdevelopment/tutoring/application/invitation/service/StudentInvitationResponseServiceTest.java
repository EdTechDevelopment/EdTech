package io.github.edtechdevelopment.tutoring.application.invitation.service;

import io.github.edtechdevelopment.tutoring.application.idempotency.model.OperationType;
import io.github.edtechdevelopment.tutoring.application.idempotency.model.PayloadFingerprint;
import io.github.edtechdevelopment.tutoring.application.idempotency.port.in.CommandIdempotency;
import io.github.edtechdevelopment.tutoring.application.invitation.exception.InvitationResponseConflictException;
import io.github.edtechdevelopment.tutoring.application.invitation.model.command.AcceptStudentInvitationCommand;
import io.github.edtechdevelopment.tutoring.application.invitation.model.command.RejectStudentInvitationCommand;
import io.github.edtechdevelopment.tutoring.application.invitation.model.result.InvitationAcceptedResult;
import io.github.edtechdevelopment.tutoring.application.invitation.model.result.InvitationRejectedResult;
import io.github.edtechdevelopment.tutoring.application.invitation.port.out.StudentInvitationRepository;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.StudentProfileRepository;
import io.github.edtechdevelopment.tutoring.application.relationship.port.out.TeacherStudentRepository;
import io.github.edtechdevelopment.tutoring.domain.invitation.model.InvitationEmail;
import io.github.edtechdevelopment.tutoring.domain.invitation.model.InvitationStatus;
import io.github.edtechdevelopment.tutoring.domain.invitation.model.StudentInvitation;
import io.github.edtechdevelopment.tutoring.domain.relationship.model.TeacherStudent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentInvitationResponseServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-01T10:00:00Z");
    private static final Instant NOW = CREATED_AT.plusSeconds(60);
    private static final UUID TEACHER_ID = UUID.randomUUID();
    private static final UUID STUDENT_ID = UUID.randomUUID();
    private static final UUID INVITATION_ID = UUID.randomUUID();
    private static final UUID OPERATION_ID = UUID.randomUUID();

    private final StudentInvitationRepository invitations = mock(StudentInvitationRepository.class);
    private final StudentProfileRepository studentProfiles = mock(StudentProfileRepository.class);
    private final TeacherStudentRepository relationships = mock(TeacherStudentRepository.class);
    private final CommandIdempotency idempotency = mock(CommandIdempotency.class);
    private final StudentInvitationResponseService service = new StudentInvitationResponseService(
            invitations, studentProfiles, relationships, idempotency, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void acceptsInvitationAndCreatesRelationship() {
        AcceptStudentInvitationCommand command = command();
        StudentInvitation invitation = invitation();
        PayloadFingerprint fingerprint = fingerprint();

        when(idempotency.beginOrReplay(OPERATION_ID, STUDENT_ID, OperationType.ACCEPT_STUDENT_INVITATION,
                fingerprint, InvitationAcceptedResult.class)).thenReturn(Optional.empty());
        when(invitations.findByIdForUpdate(INVITATION_ID)).thenReturn(Optional.of(invitation));
        when(studentProfiles.existsByUserId(STUDENT_ID)).thenReturn(true);
        when(relationships.exists(TEACHER_ID, STUDENT_ID)).thenReturn(false);

        InvitationAcceptedResult result = service.execute(command);

        assertEquals(InvitationStatus.ACCEPTED, invitation.status());
        assertEquals(INVITATION_ID, result.invitationId());
        assertEquals(TEACHER_ID, result.teacherUserId());
        assertEquals(STUDENT_ID, result.studentUserId());
        assertEquals(NOW, result.linkedAt());

        ArgumentCaptor<TeacherStudent> relationshipCaptor = ArgumentCaptor.forClass(TeacherStudent.class);
        verify(relationships).insert(relationshipCaptor.capture());
        assertEquals(TEACHER_ID, relationshipCaptor.getValue().teacherUserId());
        assertEquals(STUDENT_ID, relationshipCaptor.getValue().studentUserId());
        verify(invitations).update(invitation);
    }

    @Test
    void acceptanceRequiresStudentProfileAndDoesNotCreateRelationshipWithoutIt() {
        AcceptStudentInvitationCommand command = command();
        StudentInvitation invitation = invitation();
        PayloadFingerprint fingerprint = fingerprint();

        when(idempotency.beginOrReplay(OPERATION_ID, STUDENT_ID, OperationType.ACCEPT_STUDENT_INVITATION,
                fingerprint, InvitationAcceptedResult.class)).thenReturn(Optional.empty());
        when(invitations.findByIdForUpdate(INVITATION_ID)).thenReturn(Optional.of(invitation));
        when(studentProfiles.existsByUserId(STUDENT_ID)).thenReturn(false);

        InvitationResponseConflictException error = assertThrows(
                InvitationResponseConflictException.class, () -> service.execute(command));

        assertEquals(InvitationResponseConflictException.Reason.STUDENT_PROFILE_REQUIRED, error.reason());
        assertEquals(InvitationStatus.PENDING, invitation.status());
        verify(relationships, never()).insert(org.mockito.ArgumentMatchers.any());
        verify(invitations, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsInvitationWithoutRequiringStudentProfile() {
        RejectStudentInvitationCommand command =
                new RejectStudentInvitationCommand(OPERATION_ID, INVITATION_ID, STUDENT_ID);
        StudentInvitation invitation = invitation();
        PayloadFingerprint fingerprint = fingerprint();

        when(idempotency.beginOrReplay(OPERATION_ID, STUDENT_ID, OperationType.REJECT_STUDENT_INVITATION,
                fingerprint, InvitationRejectedResult.class)).thenReturn(Optional.empty());
        when(invitations.findByIdForUpdate(INVITATION_ID)).thenReturn(Optional.of(invitation));

        InvitationRejectedResult result = service.execute(command);

        assertEquals(InvitationStatus.REJECTED, invitation.status());
        assertEquals(InvitationStatus.REJECTED, result.status());
        verify(invitations).update(invitation);
        verify(idempotency).complete(OPERATION_ID, STUDENT_ID, OperationType.REJECT_STUDENT_INVITATION,
                fingerprint, result, NOW);
    }

    private static AcceptStudentInvitationCommand command() {
        return new AcceptStudentInvitationCommand(OPERATION_ID, INVITATION_ID, STUDENT_ID);
    }

    private static StudentInvitation invitation() {
        return StudentInvitation.create(INVITATION_ID, TEACHER_ID,
                new InvitationEmail("student@example.com"), STUDENT_ID, CREATED_AT);
    }

    private static PayloadFingerprint fingerprint() {
        return PayloadFingerprint.from(INVITATION_ID + "\n" + STUDENT_ID);
    }
}
