package io.github.edtechdevelopment.tutoring.application.integration.identity.service;

import io.github.edtechdevelopment.tutoring.application.email.port.out.NormalizedEmailLock;
import io.github.edtechdevelopment.tutoring.application.idempotency.model.PayloadFingerprint;
import io.github.edtechdevelopment.tutoring.application.idempotency.service.IdentityEventDeduplicationService;
import io.github.edtechdevelopment.tutoring.application.integration.identity.model.VerifiedAccountReference;
import io.github.edtechdevelopment.tutoring.application.integration.identity.model.command.HandleAccountEmailVerifiedCommand;
import io.github.edtechdevelopment.tutoring.application.integration.identity.port.out.IdentityAccountGateway;
import io.github.edtechdevelopment.tutoring.application.invitation.port.out.StudentInvitationRepository;
import io.github.edtechdevelopment.tutoring.domain.invitation.model.InvitationEmail;
import io.github.edtechdevelopment.tutoring.domain.invitation.model.StudentInvitation;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HandleAccountEmailVerifiedServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-01T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID STUDENT_ID = UUID.randomUUID();
    private static final String EMAIL = "student@example.com";

    private final IdentityEventDeduplicationService eventDeduplication =
            mock(IdentityEventDeduplicationService.class);
    private final IdentityAccountGateway identity = mock(IdentityAccountGateway.class);
    private final NormalizedEmailLock emailLock = mock(NormalizedEmailLock.class);
    private final StudentInvitationRepository invitations = mock(StudentInvitationRepository.class);
    private final HandleAccountEmailVerifiedService service = new HandleAccountEmailVerifiedService(
            eventDeduplication, identity, emailLock, invitations, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void attachesInvitationOnlyAfterCurrentAccountConfirmsTheSameEmail() {
        HandleAccountEmailVerifiedCommand command = new HandleAccountEmailVerifiedCommand(
                EVENT_ID, STUDENT_ID, EMAIL, NOW.minusSeconds(30));
        PayloadFingerprint fingerprint = PayloadFingerprint.from(
                "ACCOUNT_EMAIL_VERIFIED\n" + STUDENT_ID + "\n" + EMAIL + "\n" + command.verifiedAt());
        StudentInvitation invitation = StudentInvitation.create(UUID.randomUUID(), UUID.randomUUID(),
                new InvitationEmail(EMAIL), null, CREATED_AT);

        when(eventDeduplication.beginIfNotProcessed(
                EVENT_ID, STUDENT_ID, "ACCOUNT_EMAIL_VERIFIED", 1, fingerprint, NOW))
                .thenReturn(true);
        when(identity.findByVerifiedEmail(EMAIL))
                .thenReturn(java.util.Optional.of(new VerifiedAccountReference(STUDENT_ID, EMAIL)));
        when(invitations.findPendingByEmailForUpdate(new InvitationEmail(EMAIL), NOW))
                .thenReturn(List.of(invitation));

        service.handle(command);

        assertEquals(STUDENT_ID, invitation.studentUserId());
        assertEquals(NOW, invitation.attachedAt());
        verify(emailLock).lock(EMAIL);
        verify(invitations).updateAll(List.of(invitation));
    }

    @Test
    void ignoresVerificationEventWhenAccountNowUsesAnotherEmail() {
        HandleAccountEmailVerifiedCommand command = new HandleAccountEmailVerifiedCommand(
                EVENT_ID, STUDENT_ID, EMAIL, NOW.minusSeconds(30));
        PayloadFingerprint fingerprint = PayloadFingerprint.from(
                "ACCOUNT_EMAIL_VERIFIED\n" + STUDENT_ID + "\n" + EMAIL + "\n" + command.verifiedAt());
        when(eventDeduplication.beginIfNotProcessed(
                EVENT_ID, STUDENT_ID, "ACCOUNT_EMAIL_VERIFIED", 1, fingerprint, NOW)).thenReturn(true);
        when(identity.findByVerifiedEmail(EMAIL))
                .thenReturn(Optional.of(new VerifiedAccountReference(UUID.randomUUID(), EMAIL)));

        service.handle(command);

        verify(emailLock).lock(EMAIL);
        verify(invitations, never()).findPendingByEmailForUpdate(new InvitationEmail(EMAIL), NOW);
        verify(invitations, never()).updateAll(org.mockito.ArgumentMatchers.anyList());
    }
}
