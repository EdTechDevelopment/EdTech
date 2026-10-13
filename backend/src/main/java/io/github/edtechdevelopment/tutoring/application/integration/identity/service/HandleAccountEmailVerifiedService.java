package io.github.edtechdevelopment.tutoring.application.integration.identity.service;

import io.github.edtechdevelopment.tutoring.application.email.port.out.NormalizedEmailLock;
import io.github.edtechdevelopment.tutoring.application.idempotency.model.PayloadFingerprint;
import io.github.edtechdevelopment.tutoring.application.idempotency.service.IdentityEventDeduplicationService;
import io.github.edtechdevelopment.tutoring.application.integration.identity.model.command.HandleAccountEmailVerifiedCommand;
import io.github.edtechdevelopment.tutoring.application.integration.identity.port.out.IdentityAccountGateway;
import io.github.edtechdevelopment.tutoring.application.integration.identity.port.in.HandleAccountEmailVerifiedUseCase;
import io.github.edtechdevelopment.tutoring.application.invitation.port.out.StudentInvitationRepository;
import io.github.edtechdevelopment.tutoring.domain.invitation.exception.SelfInvitationException;
import io.github.edtechdevelopment.tutoring.domain.invitation.model.InvitationEmail;
import io.github.edtechdevelopment.tutoring.domain.invitation.model.StudentInvitation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class HandleAccountEmailVerifiedService implements HandleAccountEmailVerifiedUseCase {

    private static final String EVENT_TYPE = "ACCOUNT_EMAIL_VERIFIED";
    private static final int FINGERPRINT_VERSION = 1;

    private final IdentityEventDeduplicationService eventDeduplication;
    private final IdentityAccountGateway identity;
    private final NormalizedEmailLock emailLock;
    private final StudentInvitationRepository invitations;
    private final Clock clock;

    public HandleAccountEmailVerifiedService(
            IdentityEventDeduplicationService eventDeduplication,
            IdentityAccountGateway identity,
            NormalizedEmailLock emailLock,
            StudentInvitationRepository invitations,
            Clock clock) {

        this.eventDeduplication = Objects.requireNonNull(eventDeduplication, "Event deduplication service must not be null");
        this.identity = Objects.requireNonNull(identity, "Identity gateway must not be null");
        this.emailLock = Objects.requireNonNull(emailLock, "Normalized email lock must not be null");
        this.invitations = Objects.requireNonNull(invitations, "Invitation repository must not be null");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
    }

    @Override
    @Transactional
    public void handle(HandleAccountEmailVerifiedCommand command) {
        Objects.requireNonNull(command, "Verified account email command must not be null");
        InvitationEmail email = new InvitationEmail(command.verifiedEmail());
        PayloadFingerprint fingerprint = PayloadFingerprint.from(
                EVENT_TYPE + "\n" + command.userId() + "\n" + email.value() + "\n" + command.verifiedAt());
        var now = clock.instant();
        if (!eventDeduplication.beginIfNotProcessed(command.eventId(), command.userId(), EVENT_TYPE,
                FINGERPRINT_VERSION, fingerprint, now)) {
            return;
        }

        emailLock.lock(email.value());
        var currentAccount = identity.findByVerifiedEmail(email.value());
        if (currentAccount.isEmpty() || !currentAccount.get().userId().equals(command.userId())) {
            return;
        }
        attachPendingInvitations(email, command.userId(), now);
    }

    private void attachPendingInvitations(InvitationEmail email, UUID userId, Instant now) {
        List<StudentInvitation> changed = new ArrayList<>();
        for (StudentInvitation invitation : invitations.findPendingByEmailForUpdate(email, now)) {
            if (invitation.studentUserId() != null) {
                continue;
            }
            try {
                if (invitation.attachStudent(userId, now)) {
                    changed.add(invitation);
                }
            } catch (SelfInvitationException ignored) {
            }
        }
        if (!changed.isEmpty()) {
            invitations.updateAll(changed);
        }
    }
}
