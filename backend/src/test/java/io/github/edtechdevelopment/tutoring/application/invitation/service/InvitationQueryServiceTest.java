package io.github.edtechdevelopment.tutoring.application.invitation.service;

import io.github.edtechdevelopment.tutoring.api.model.profile.summary.TeacherProfileSummary;
import io.github.edtechdevelopment.tutoring.api.query.TutoringProfileQuery;
import io.github.edtechdevelopment.tutoring.application.invitation.mapper.InvitationResultMapper;
import io.github.edtechdevelopment.tutoring.application.invitation.model.query.IncomingInvitationsQuery;
import io.github.edtechdevelopment.tutoring.application.invitation.model.query.InvitationPageRow;
import io.github.edtechdevelopment.tutoring.application.invitation.model.query.SentInvitationsQuery;
import io.github.edtechdevelopment.tutoring.application.invitation.port.out.InvitationCursorCodec;
import io.github.edtechdevelopment.tutoring.application.invitation.port.out.StudentInvitationRepository;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.TeacherProfileRepository;
import io.github.edtechdevelopment.tutoring.domain.invitation.model.InvitationEmail;
import io.github.edtechdevelopment.tutoring.domain.invitation.model.InvitationStatus;
import io.github.edtechdevelopment.tutoring.domain.invitation.model.StudentInvitation;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InvitationQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");
    private static final UUID STUDENT_ID = UUID.randomUUID();
    private static final UUID TEACHER_ID = UUID.randomUUID();

    private final StudentInvitationRepository invitations = mock(StudentInvitationRepository.class);
    private final TeacherProfileRepository teacherProfiles = mock(TeacherProfileRepository.class);
    private final InvitationCursorCodec cursorCodec = mock(InvitationCursorCodec.class);
    private final TutoringProfileQuery profiles = mock(TutoringProfileQuery.class);
    private final InvitationQueryService service = new InvitationQueryService(
            teacherProfiles, invitations, cursorCodec, profiles,
            new InvitationResultMapper(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void listsInvitationForVerifiedAccountBeforeStudentProfileExists() {
        StudentInvitation invitation = StudentInvitation.create(UUID.randomUUID(), TEACHER_ID,
                new InvitationEmail("student@example.com"), STUDENT_ID, NOW.minusSeconds(60));
        when(invitations.findIncomingPage(STUDENT_ID, InvitationStatus.PENDING, NOW, null, 21))
                .thenReturn(List.of(new InvitationPageRow(invitation, InvitationStatus.PENDING)));
        when(profiles.findTeacherSummaries(Set.of(TEACHER_ID)))
                .thenReturn(Map.of(TEACHER_ID, new TeacherProfileSummary(TEACHER_ID, "Teacher")));

        var page = service.execute(new IncomingInvitationsQuery(STUDENT_ID, InvitationStatus.PENDING, null, 20));

        assertEquals(1, page.items().size());
        assertEquals(invitation.id(), page.items().getFirst().invitationId());
        assertEquals(TEACHER_ID, page.items().getFirst().teacher().userId());
        assertEquals("PENDING", page.items().getFirst().status());
        verify(invitations).findIncomingPage(STUDENT_ID, InvitationStatus.PENDING, NOW, null, 21);
        verifyNoInteractions(cursorCodec);
    }

    @Test
    void listsSentInvitationsAfterCheckingTeacherProfile() {
        StudentInvitation invitation = StudentInvitation.create(UUID.randomUUID(), TEACHER_ID,
                new InvitationEmail("student@example.com"), null, NOW.minusSeconds(60));
        when(teacherProfiles.existsByUserId(TEACHER_ID)).thenReturn(true);
        when(invitations.findSentPage(TEACHER_ID, InvitationStatus.PENDING, NOW, null, 21))
                .thenReturn(List.of(new InvitationPageRow(invitation, InvitationStatus.PENDING)));

        var page = service.execute(new SentInvitationsQuery(TEACHER_ID, InvitationStatus.PENDING, null, 20));

        assertEquals(1, page.items().size());
        assertEquals(invitation.id(), page.items().getFirst().invitationId());
        assertEquals("PENDING", page.items().getFirst().status());
        verify(teacherProfiles).existsByUserId(TEACHER_ID);
        verify(invitations).findSentPage(TEACHER_ID, InvitationStatus.PENDING, NOW, null, 21);
    }
}
