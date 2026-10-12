package io.github.edtechdevelopment.tutoring.application.invitation.service;

import io.github.edtechdevelopment.tutoring.application.email.port.out.NormalizedEmailLock;
import io.github.edtechdevelopment.tutoring.application.idempotency.model.OperationType;
import io.github.edtechdevelopment.tutoring.application.idempotency.model.PayloadFingerprint;
import io.github.edtechdevelopment.tutoring.application.idempotency.port.in.CommandIdempotency;
import io.github.edtechdevelopment.tutoring.application.integration.identity.model.VerifiedAccountReference;
import io.github.edtechdevelopment.tutoring.application.integration.identity.port.out.IdentityAccountGateway;
import io.github.edtechdevelopment.tutoring.application.invitation.mapper.InvitationResultMapper;
import io.github.edtechdevelopment.tutoring.application.invitation.model.command.CreateStudentInvitationCommand;
import io.github.edtechdevelopment.tutoring.application.invitation.model.notification.StudentInvitationNotification;
import io.github.edtechdevelopment.tutoring.application.invitation.model.result.InvitationCreatedResult;
import io.github.edtechdevelopment.tutoring.application.invitation.port.out.InvitationNotificationSender;
import io.github.edtechdevelopment.tutoring.application.invitation.port.out.StudentInvitationRepository;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.TeacherProfileRepository;
import io.github.edtechdevelopment.tutoring.application.relationship.port.out.TeacherStudentRepository;
import io.github.edtechdevelopment.tutoring.domain.invitation.model.InvitationEmail;
import io.github.edtechdevelopment.tutoring.domain.invitation.model.StudentInvitation;
import io.github.edtechdevelopment.tutoring.domain.profile.model.ProfileEmail;
import io.github.edtechdevelopment.tutoring.domain.profile.model.TeacherProfile;
import io.github.edtechdevelopment.tutoring.domain.subject.model.SubjectCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreateStudentInvitationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");
    private static final UUID TEACHER_ID = UUID.randomUUID();
    private static final UUID OPERATION_ID = UUID.randomUUID();
    private static final String STUDENT_EMAIL = "student@example.com";

    private final TeacherProfileRepository teacherProfiles = mock(TeacherProfileRepository.class);
    private final TeacherStudentRepository relationships = mock(TeacherStudentRepository.class);
    private final StudentInvitationRepository invitations = mock(StudentInvitationRepository.class);
    private final IdentityAccountGateway identity = mock(IdentityAccountGateway.class);
    private final NormalizedEmailLock emailLock = mock(NormalizedEmailLock.class);
    private final InvitationNotificationSender notifications = mock(InvitationNotificationSender.class);
    private final CommandIdempotency idempotency = mock(CommandIdempotency.class);
    private final CreateStudentInvitationService service = new CreateStudentInvitationService(
            teacherProfiles, relationships, invitations, identity, emailLock, notifications,
            idempotency, new InvitationResultMapper(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void createsUnattachedInvitationForAnUnregisteredEmail() {
        CreateStudentInvitationCommand command = new CreateStudentInvitationCommand(
                OPERATION_ID, TEACHER_ID, " Student@Example.com ");
        PayloadFingerprint fingerprint = PayloadFingerprint.from(TEACHER_ID + "\n" + STUDENT_EMAIL);
        TeacherProfile teacher = teacherProfile();

        when(idempotency.beginOrReplay(OPERATION_ID, TEACHER_ID, OperationType.CREATE_STUDENT_INVITATION,
                fingerprint, InvitationCreatedResult.class)).thenReturn(Optional.empty());
        when(teacherProfiles.findByUserId(TEACHER_ID)).thenReturn(Optional.of(teacher));
        when(identity.findVerifiedAccountById(TEACHER_ID))
                .thenReturn(Optional.of(new VerifiedAccountReference(TEACHER_ID, "teacher@example.com")));
        when(invitations.findPendingByTeacherAndEmailForUpdate(
                TEACHER_ID, new InvitationEmail(STUDENT_EMAIL))).thenReturn(List.of());
        when(identity.findByVerifiedEmail(STUDENT_EMAIL)).thenReturn(Optional.empty());

        InvitationCreatedResult result = service.execute(command);

        ArgumentCaptor<StudentInvitation> invitationCaptor = ArgumentCaptor.forClass(StudentInvitation.class);
        verify(invitations).insert(invitationCaptor.capture());
        StudentInvitation invitation = invitationCaptor.getValue();
        assertEquals(result.invitationId(), invitation.id());
        assertEquals(new InvitationEmail(STUDENT_EMAIL), invitation.studentEmail());
        assertNull(invitation.studentUserId());
        assertEquals(NOW.plusSeconds(30L * 24 * 60 * 60), result.expiresAt());

        ArgumentCaptor<StudentInvitationNotification> notificationCaptor =
                ArgumentCaptor.forClass(StudentInvitationNotification.class);
        verify(notifications).enqueue(notificationCaptor.capture());
        assertEquals(result.invitationId(), notificationCaptor.getValue().invitationId());
        verify(emailLock).lock(STUDENT_EMAIL);
    }

    @Test
    void attachesInvitationImmediatelyWhenRecipientHasThatVerifiedEmail() {
        CreateStudentInvitationCommand command = new CreateStudentInvitationCommand(
                OPERATION_ID, TEACHER_ID, STUDENT_EMAIL);
        PayloadFingerprint fingerprint = PayloadFingerprint.from(TEACHER_ID + "\n" + STUDENT_EMAIL);
        UUID studentId = UUID.randomUUID();

        when(idempotency.beginOrReplay(OPERATION_ID, TEACHER_ID, OperationType.CREATE_STUDENT_INVITATION,
                fingerprint, InvitationCreatedResult.class)).thenReturn(Optional.empty());
        when(teacherProfiles.findByUserId(TEACHER_ID)).thenReturn(Optional.of(teacherProfile()));
        when(identity.findVerifiedAccountById(TEACHER_ID))
                .thenReturn(Optional.of(new VerifiedAccountReference(TEACHER_ID, "teacher@example.com")));
        when(invitations.findPendingByTeacherAndEmailForUpdate(
                TEACHER_ID, new InvitationEmail(STUDENT_EMAIL))).thenReturn(List.of());
        when(identity.findByVerifiedEmail(STUDENT_EMAIL))
                .thenReturn(Optional.of(new VerifiedAccountReference(studentId, STUDENT_EMAIL)));
        when(relationships.exists(TEACHER_ID, studentId)).thenReturn(false);

        InvitationCreatedResult result = service.execute(command);

        ArgumentCaptor<StudentInvitation> invitationCaptor = ArgumentCaptor.forClass(StudentInvitation.class);
        verify(invitations).insert(invitationCaptor.capture());
        assertEquals(studentId, invitationCaptor.getValue().studentUserId());
        assertEquals(NOW, invitationCaptor.getValue().attachedAt());
        verify(identity, never()).requireRole(studentId, io.github.edtechdevelopment.tutoring.domain.profile.model.ProfileType.STUDENT);
        verify(notifications).enqueue(org.mockito.ArgumentMatchers.argThat(
                notification -> notification.invitationId().equals(result.invitationId())));
    }

    private static TeacherProfile teacherProfile() {
        return TeacherProfile.create(TEACHER_ID, "Teacher", new ProfileEmail("teacher@example.com"),
                List.of(), Set.of(new SubjectCode("MATH")), null, null, null, null, null, NOW);
    }
}
