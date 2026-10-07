package io.github.edtechdevelopment.tutoring.domain.invitation.model;

import io.github.edtechdevelopment.tutoring.domain.invitation.exception.InvalidInvitationEmailException;
import io.github.edtechdevelopment.tutoring.domain.invitation.exception.InvalidInvitationStateException;
import io.github.edtechdevelopment.tutoring.domain.invitation.exception.InvitationExpiredException;
import io.github.edtechdevelopment.tutoring.domain.invitation.exception.InvitationOwnershipException;
import io.github.edtechdevelopment.tutoring.domain.invitation.exception.SelfInvitationException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudentInvitationTest {

    private static final Instant START = Instant.parse("2026-10-01T10:00:00Z");

    @Test
    void normalizesInvitationEmail() {
        assertEquals("student@example.com", new InvitationEmail(" Student@Example.COM ").value());
        assertThrows(InvalidInvitationEmailException.class, () -> new InvitationEmail("student@@example.com"));
    }

    @Test
    void attachesOnceAndOnlyToOriginalStudent() {
        UUID teacherId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        StudentInvitation invitation = invitation(teacherId, null);

        assertTrue(invitation.attachStudent(studentId, START.plusSeconds(10)));
        assertFalse(invitation.attachStudent(studentId, START.plusSeconds(20)));
        assertEquals(START.plusSeconds(10), invitation.attachedAt());
        assertThrows(InvitationOwnershipException.class,
                () -> invitation.attachStudent(UUID.randomUUID(), START.plusSeconds(20)));
        assertThrows(SelfInvitationException.class,
                () -> invitation.attachStudent(teacherId, START.plusSeconds(20)));
    }

    @Test
    void acceptsOnlyAttachedStudentBeforeDeadline() {
        UUID studentId = UUID.randomUUID();
        StudentInvitation invitation = invitation(UUID.randomUUID(), studentId);

        assertThrows(InvitationOwnershipException.class,
                () -> invitation.accept(UUID.randomUUID(), START.plusSeconds(1)));
        invitation.accept(studentId, START.plusSeconds(1));

        assertEquals(InvitationStatus.ACCEPTED, invitation.status());
        assertEquals(START.plusSeconds(1), invitation.respondedAt());
        assertThrows(InvalidInvitationStateException.class,
                () -> invitation.reject(studentId, START.plusSeconds(2)));
        assertEquals(InvitationStatus.ACCEPTED, invitation.effectiveStatusAt(START.plus(Duration.ofDays(31))));
    }

    @Test
    void rejectsExpiredResponseEvenWhenStoredStatusIsPending() {
        UUID studentId = UUID.randomUUID();
        StudentInvitation invitation = invitation(UUID.randomUUID(), studentId);
        Instant deadline = START.plus(Duration.ofDays(30));

        assertEquals(InvitationStatus.EXPIRED, invitation.effectiveStatusAt(deadline));
        assertEquals(InvitationStatus.PENDING, invitation.status());
        assertThrows(InvitationExpiredException.class, () -> invitation.accept(studentId, deadline));
        assertTrue(invitation.expire(deadline));
        assertFalse(invitation.expire(deadline.plusSeconds(1)));
        assertEquals(InvitationStatus.EXPIRED, invitation.status());
        assertNull(invitation.respondedAt());
    }

    @Test
    void rejectsExpiredAttachmentAndSelfInvitation() {
        UUID teacherId = UUID.randomUUID();
        StudentInvitation invitation = invitation(teacherId, null);

        assertThrows(InvitationExpiredException.class,
                () -> invitation.attachStudent(UUID.randomUUID(), invitation.expiresAt()));
        assertThrows(SelfInvitationException.class, () -> invitation(teacherId, teacherId));
    }

    @Test
    void rejectsResponseBeforeAttachment() {
        UUID studentId = UUID.randomUUID();
        StudentInvitation invitation = invitation(UUID.randomUUID(), null);
        invitation.attachStudent(studentId, START.plusSeconds(10));

        assertThrows(InvalidInvitationStateException.class,
                () -> invitation.reject(studentId, START.plusSeconds(9)));
        assertEquals(InvitationStatus.PENDING, invitation.status());
    }

    @Test
    void restoredStateMustHaveConsistentAttachmentAndResponse() {
        UUID teacherId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        StudentInvitation invitation = invitation(teacherId, studentId);

        assertThrows(InvalidInvitationStateException.class,
                () -> StudentInvitation.reconstitute(invitation.id(), teacherId, invitation.studentEmail(), studentId,
                        null, InvitationStatus.ACCEPTED, START, invitation.expiresAt(), START.plusSeconds(1)));
    }

    private static StudentInvitation invitation(UUID teacherId, UUID studentId) {
        return StudentInvitation.create(UUID.randomUUID(), teacherId,
                new InvitationEmail("student@example.com"), studentId, START);
    }
}
