package io.github.edtechdevelopment.tutoring.application.invitation.service;

import io.github.edtechdevelopment.tutoring.api.exception.ProfileNotFoundException;
import io.github.edtechdevelopment.tutoring.api.model.profile.ProfileTypeView;
import io.github.edtechdevelopment.tutoring.application.email.port.out.NormalizedEmailLock;
import io.github.edtechdevelopment.tutoring.application.idempotency.model.OperationType;
import io.github.edtechdevelopment.tutoring.application.idempotency.model.PayloadFingerprint;
import io.github.edtechdevelopment.tutoring.application.idempotency.port.in.CommandIdempotency;
import io.github.edtechdevelopment.tutoring.application.integration.identity.exception.IdentityUserConsistencyException;
import io.github.edtechdevelopment.tutoring.application.integration.identity.model.VerifiedAccountReference;
import io.github.edtechdevelopment.tutoring.application.integration.identity.port.out.IdentityAccountGateway;
import io.github.edtechdevelopment.tutoring.application.invitation.exception.InvitationCreationConflictException;
import io.github.edtechdevelopment.tutoring.application.invitation.mapper.InvitationResultMapper;
import io.github.edtechdevelopment.tutoring.application.invitation.model.command.CreateStudentInvitationCommand;
import io.github.edtechdevelopment.tutoring.application.invitation.model.notification.StudentInvitationNotification;
import io.github.edtechdevelopment.tutoring.application.invitation.model.result.InvitationCreatedResult;
import io.github.edtechdevelopment.tutoring.application.invitation.port.in.CreateStudentInvitationUseCase;
import io.github.edtechdevelopment.tutoring.application.invitation.port.out.InvitationNotificationSender;
import io.github.edtechdevelopment.tutoring.application.invitation.port.out.StudentInvitationRepository;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.TeacherProfileRepository;
import io.github.edtechdevelopment.tutoring.application.relationship.port.out.TeacherStudentRepository;
import io.github.edtechdevelopment.tutoring.domain.invitation.model.InvitationEmail;
import io.github.edtechdevelopment.tutoring.domain.invitation.model.InvitationStatus;
import io.github.edtechdevelopment.tutoring.domain.invitation.model.StudentInvitation;
import io.github.edtechdevelopment.tutoring.domain.profile.model.ProfileType;
import io.github.edtechdevelopment.tutoring.domain.profile.model.TeacherProfile;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class CreateStudentInvitationService implements CreateStudentInvitationUseCase {

    private static final OperationType OPERATION_TYPE = OperationType.CREATE_STUDENT_INVITATION;

    private final TeacherProfileRepository teacherProfiles;
    private final TeacherStudentRepository relationships;
    private final StudentInvitationRepository invitations;
    private final IdentityAccountGateway identity;
    private final NormalizedEmailLock emailLock;
    private final InvitationNotificationSender notifications;
    private final CommandIdempotency idempotency;
    private final InvitationResultMapper mapper;
    private final Clock clock;

    public CreateStudentInvitationService(
            TeacherProfileRepository teacherProfiles,
            TeacherStudentRepository relationships,
            StudentInvitationRepository invitations,
            IdentityAccountGateway identity,
            NormalizedEmailLock emailLock,
            InvitationNotificationSender notifications,
            CommandIdempotency idempotency,
            InvitationResultMapper mapper,
            Clock clock) {

        this.teacherProfiles = Objects.requireNonNull(teacherProfiles, "Teacher profile repository must not be null");
        this.relationships = Objects.requireNonNull(relationships, "Relationship repository must not be null");
        this.invitations = Objects.requireNonNull(invitations, "Invitation repository must not be null");
        this.identity = Objects.requireNonNull(identity, "Identity gateway must not be null");
        this.emailLock = Objects.requireNonNull(emailLock, "Normalized email lock must not be null");
        this.notifications = Objects.requireNonNull(notifications, "Invitation notification sender must not be null");
        this.idempotency = Objects.requireNonNull(idempotency, "Command idempotency must not be null");
        this.mapper = Objects.requireNonNull(mapper, "Invitation result mapper must not be null");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
    }

    @Override
    @Transactional
    public InvitationCreatedResult execute(CreateStudentInvitationCommand command) {
        Objects.requireNonNull(command, "Create invitation command must not be null");
        InvitationEmail studentEmail;
        try { studentEmail = new InvitationEmail(command.studentEmail()); }
        catch (io.github.edtechdevelopment.tutoring.domain.invitation.exception.InvalidInvitationEmailException exception) {
            throw new IllegalArgumentException("Invitation email is invalid");
        }
        PayloadFingerprint fingerprint = PayloadFingerprint.from(
                command.teacherUserId() + "\n" + studentEmail.value());

        Optional<InvitationCreatedResult> replay = idempotency.beginOrReplay(
                command.operationId(), command.teacherUserId(), OPERATION_TYPE, fingerprint,
                InvitationCreatedResult.class);
        if (replay.isPresent()) {
            return replay.get();
        }

        emailLock.lock(studentEmail.value());
        TeacherProfile teacher = teacherProfiles.findByUserId(command.teacherUserId())
                .orElseThrow(() -> new ProfileNotFoundException(ProfileTypeView.TEACHER, command.teacherUserId()));
        VerifiedAccountReference teacherAccount = identity.findVerifiedAccountById(command.teacherUserId())
                .orElseThrow(() -> new IdentityUserConsistencyException(command.teacherUserId(), ProfileType.TEACHER));
        if (teacherAccount.email().equals(studentEmail.value())) {
            throw new InvitationCreationConflictException(InvitationCreationConflictException.Reason.SELF_INVITATION);
        }

        Instant now = clock.instant();
        expireOldPendingInvitations(command.teacherUserId(), studentEmail, now);
        VerifiedAccountReference recipient = identity.findByVerifiedEmail(studentEmail.value()).orElse(null);
        if (recipient != null && recipient.userId().equals(command.teacherUserId())) {
            throw new InvitationCreationConflictException(InvitationCreationConflictException.Reason.SELF_INVITATION);
        }
        if (recipient != null && relationships.exists(command.teacherUserId(), recipient.userId())) {
            throw new InvitationCreationConflictException(InvitationCreationConflictException.Reason.ALREADY_LINKED);
        }

        StudentInvitation invitation = StudentInvitation.create(
                UUID.randomUUID(), command.teacherUserId(), studentEmail,
                recipient == null ? null : recipient.userId(), now);
        invitations.insert(invitation);
        notifications.enqueue(new StudentInvitationNotification(
                invitation.id(), invitation.studentEmail(), teacher.displayName(), invitation.expiresAt(),
                "INVITATION_CREATED:" + invitation.id()));

        InvitationCreatedResult result = mapper.toCreatedResult(invitation);
        idempotency.complete(command.operationId(), command.teacherUserId(), OPERATION_TYPE,
                fingerprint, result, now);
        return result;
    }

    private void expireOldPendingInvitations(UUID teacherUserId, InvitationEmail email, Instant now) {
        List<StudentInvitation> expired = new ArrayList<>();
        List<StudentInvitation> pending = invitations.findPendingByTeacherAndEmailForUpdate(teacherUserId, email);
        for (StudentInvitation invitation : pending) {
            if (invitation.effectiveStatusAt(now) == InvitationStatus.PENDING) {
                throw new InvitationCreationConflictException(InvitationCreationConflictException.Reason.ALREADY_PENDING);
            }
            invitation.expire(now);
            expired.add(invitation);
        }
        if (!expired.isEmpty()) {
            invitations.updateAll(expired);
        }
    }
}
