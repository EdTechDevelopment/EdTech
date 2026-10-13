package io.github.edtechdevelopment.tutoring.application.invitation.service;

import io.github.edtechdevelopment.tutoring.application.idempotency.model.OperationType;
import io.github.edtechdevelopment.tutoring.application.idempotency.model.PayloadFingerprint;
import io.github.edtechdevelopment.tutoring.application.idempotency.port.in.CommandIdempotency;
import io.github.edtechdevelopment.tutoring.application.invitation.exception.InvitationResponseConflictException;
import io.github.edtechdevelopment.tutoring.application.invitation.exception.InvitationUnavailableException;
import io.github.edtechdevelopment.tutoring.application.invitation.model.command.AcceptStudentInvitationCommand;
import io.github.edtechdevelopment.tutoring.application.invitation.model.command.RejectStudentInvitationCommand;
import io.github.edtechdevelopment.tutoring.application.invitation.model.result.InvitationAcceptedResult;
import io.github.edtechdevelopment.tutoring.application.invitation.model.result.InvitationRejectedResult;
import io.github.edtechdevelopment.tutoring.application.invitation.port.in.AcceptStudentInvitationUseCase;
import io.github.edtechdevelopment.tutoring.application.invitation.port.in.RejectStudentInvitationUseCase;
import io.github.edtechdevelopment.tutoring.application.invitation.port.out.StudentInvitationRepository;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.StudentProfileRepository;
import io.github.edtechdevelopment.tutoring.application.relationship.port.out.TeacherStudentRepository;
import io.github.edtechdevelopment.tutoring.application.relationship.exception.RelationshipAlreadyExistsException;
import io.github.edtechdevelopment.tutoring.domain.invitation.model.InvitationStatus;
import io.github.edtechdevelopment.tutoring.domain.invitation.model.StudentInvitation;
import io.github.edtechdevelopment.tutoring.domain.relationship.model.TeacherStudent;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class StudentInvitationResponseService implements AcceptStudentInvitationUseCase, RejectStudentInvitationUseCase {

    private static final OperationType ACCEPT_OPERATION = OperationType.ACCEPT_STUDENT_INVITATION;
    private static final OperationType REJECT_OPERATION = OperationType.REJECT_STUDENT_INVITATION;

    private final StudentInvitationRepository invitations;
    private final StudentProfileRepository studentProfiles;
    private final TeacherStudentRepository relationships;
    private final CommandIdempotency idempotency;
    private final Clock clock;

    public StudentInvitationResponseService(
            StudentInvitationRepository invitations,
            StudentProfileRepository studentProfiles,
            TeacherStudentRepository relationships,
            CommandIdempotency idempotency,
            Clock clock) {

        this.invitations = Objects.requireNonNull(invitations, "Invitation repository must not be null");
        this.studentProfiles = Objects.requireNonNull(studentProfiles, "Student profile repository must not be null");
        this.relationships = Objects.requireNonNull(relationships, "Relationship repository must not be null");
        this.idempotency = Objects.requireNonNull(idempotency, "Command idempotency must not be null");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
    }

    @Override
    @Transactional
    public InvitationAcceptedResult execute(AcceptStudentInvitationCommand command) {
        Objects.requireNonNull(command, "Accept invitation command must not be null");
        PayloadFingerprint fingerprint = fingerprint(command.invitationId(), command.studentUserId());
        Optional<InvitationAcceptedResult> replay = idempotency.beginOrReplay(
                command.operationId(), command.studentUserId(), ACCEPT_OPERATION, fingerprint,
                InvitationAcceptedResult.class);
        if (replay.isPresent()) {
            return replay.get();
        }

        Instant now = clock.instant();
        StudentInvitation invitation = findPendingForStudent(command.invitationId(), command.studentUserId(), now);
        if (!studentProfiles.existsByUserId(command.studentUserId())) {
            throw new InvitationResponseConflictException(command.invitationId(),
                    InvitationResponseConflictException.Reason.STUDENT_PROFILE_REQUIRED);
        }
        if (relationships.exists(invitation.teacherUserId(), command.studentUserId())) {
            throw new InvitationResponseConflictException(command.invitationId(),
                    InvitationResponseConflictException.Reason.ALREADY_LINKED);
        }

        invitation.accept(command.studentUserId(), now);
        TeacherStudent relationship = TeacherStudent.create(invitation.teacherUserId(), command.studentUserId(), now);
        invitations.update(invitation);
        try {
            relationships.insert(relationship);
        } catch (RelationshipAlreadyExistsException exception) {
            throw new InvitationResponseConflictException(command.invitationId(),
                    InvitationResponseConflictException.Reason.ALREADY_LINKED);
        }

        InvitationAcceptedResult result = new InvitationAcceptedResult(
                invitation.id(), relationship.teacherUserId(), relationship.studentUserId(), relationship.createdAt());
        idempotency.complete(command.operationId(), command.studentUserId(), ACCEPT_OPERATION,
                fingerprint, result, now);
        return result;
    }

    @Override
    @Transactional
    public InvitationRejectedResult execute(RejectStudentInvitationCommand command) {
        Objects.requireNonNull(command, "Reject invitation command must not be null");
        PayloadFingerprint fingerprint = fingerprint(command.invitationId(), command.studentUserId());
        Optional<InvitationRejectedResult> replay = idempotency.beginOrReplay(
                command.operationId(), command.studentUserId(), REJECT_OPERATION, fingerprint,
                InvitationRejectedResult.class);
        if (replay.isPresent()) {
            return replay.get();
        }

        Instant now = clock.instant();
        StudentInvitation invitation = findPendingForStudent(command.invitationId(), command.studentUserId(), now);
        invitation.reject(command.studentUserId(), now);
        invitations.update(invitation);

        InvitationRejectedResult result = new InvitationRejectedResult(invitation.id(), invitation.status().name());
        idempotency.complete(command.operationId(), command.studentUserId(), REJECT_OPERATION,
                fingerprint, result, now);
        return result;
    }

    private StudentInvitation findPendingForStudent(UUID invitationId, UUID studentUserId, Instant now) {
        StudentInvitation invitation = invitations.findByIdForUpdate(invitationId)
                .orElseThrow(() -> new InvitationUnavailableException(invitationId));
        if (!studentUserId.equals(invitation.studentUserId())) {
            throw new InvitationUnavailableException(invitation.id());
        }

        InvitationStatus status = invitation.effectiveStatusAt(now);
        if (status == InvitationStatus.EXPIRED) {
            throw new InvitationResponseConflictException(invitation.id(), InvitationResponseConflictException.Reason.EXPIRED);
        }
        if (status != InvitationStatus.PENDING) {
            throw new InvitationResponseConflictException(
                    invitation.id(), InvitationResponseConflictException.Reason.ALREADY_RESPONDED);
        }
        return invitation;
    }

    private static PayloadFingerprint fingerprint(UUID invitationId, UUID studentUserId) {
        return PayloadFingerprint.from(invitationId + "\n" + studentUserId);
    }
}
