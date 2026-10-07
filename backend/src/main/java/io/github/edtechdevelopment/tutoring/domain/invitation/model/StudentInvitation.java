package io.github.edtechdevelopment.tutoring.domain.invitation.model;

import io.github.edtechdevelopment.tutoring.domain.invitation.exception.InvalidInvitationStateException;
import io.github.edtechdevelopment.tutoring.domain.invitation.exception.InvitationExpiredException;
import io.github.edtechdevelopment.tutoring.domain.invitation.exception.InvitationOwnershipException;
import io.github.edtechdevelopment.tutoring.domain.invitation.exception.SelfInvitationException;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class StudentInvitation {

    private static final Duration VALIDITY = Duration.ofDays(30);

    private final UUID id;
    private final UUID teacherUserId;
    private final InvitationEmail studentEmail;
    private UUID studentUserId;
    private Instant attachedAt;
    private InvitationStatus status;
    private final Instant createdAt;
    private final Instant expiresAt;
    private Instant respondedAt;

    private StudentInvitation(
            UUID id, UUID teacherUserId, InvitationEmail studentEmail, UUID studentUserId,
            Instant attachedAt, InvitationStatus status, Instant createdAt, Instant expiresAt, Instant respondedAt) {

        this.id = Objects.requireNonNull(id, "Invitation id must not be null");
        this.teacherUserId = Objects.requireNonNull(teacherUserId, "Teacher user id must not be null");
        this.studentEmail = Objects.requireNonNull(studentEmail, "Student email must not be null");
        this.studentUserId = studentUserId;
        this.attachedAt = attachedAt;
        this.status = Objects.requireNonNull(status, "Invitation status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "Creation time must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "Expiration time must not be null");
        this.respondedAt = respondedAt;
        validateState();
    }

    public static StudentInvitation create(
            UUID id, UUID teacherUserId, InvitationEmail studentEmail, UUID studentUserId, Instant createdAt) {

        Objects.requireNonNull(createdAt, "Creation time must not be null");
        return new StudentInvitation(id, teacherUserId, studentEmail, studentUserId,
                studentUserId == null ? null : createdAt, InvitationStatus.PENDING,
                createdAt, createdAt.plus(VALIDITY), null);
    }

    public static StudentInvitation reconstitute(
            UUID id, UUID teacherUserId, InvitationEmail studentEmail, UUID studentUserId,
            Instant attachedAt, InvitationStatus status, Instant createdAt, Instant expiresAt, Instant respondedAt) {

        return new StudentInvitation(id, teacherUserId, studentEmail, studentUserId,
                attachedAt, status, createdAt, expiresAt, respondedAt);
    }

    public boolean attachStudent(UUID studentUserId, Instant now) {
        Objects.requireNonNull(studentUserId, "Student user id must not be null");
        Objects.requireNonNull(now, "Attachment time must not be null");
        requirePending("attach student");
        requireNotExpired(now);
        if (now.isBefore(createdAt)) {
            throw new InvalidInvitationStateException(id, status, "attach student before creation");
        }
        if (teacherUserId.equals(studentUserId)) {
            throw new SelfInvitationException("Teacher cannot invite themselves");
        }
        if (this.studentUserId != null) {
            if (!this.studentUserId.equals(studentUserId)) {
                throw new InvitationOwnershipException(id);
            }
            return false;
        }

        this.studentUserId = studentUserId;
        this.attachedAt = now;
        return true;
    }

    public void accept(UUID actorStudentUserId, Instant now) {
        respond(actorStudentUserId, now, InvitationStatus.ACCEPTED, "accept");
    }

    public void reject(UUID actorStudentUserId, Instant now) {
        respond(actorStudentUserId, now, InvitationStatus.REJECTED, "reject");
    }

    public boolean isExpiredAt(Instant now) {
        Objects.requireNonNull(now, "Current time must not be null");
        return !now.isBefore(expiresAt);
    }

    public InvitationStatus effectiveStatusAt(Instant now) {
        Objects.requireNonNull(now, "Current time must not be null");
        return status == InvitationStatus.PENDING && isExpiredAt(now) ? InvitationStatus.EXPIRED : status;
    }

    public boolean expire(Instant now) {
        Objects.requireNonNull(now, "Current time must not be null");
        if (status != InvitationStatus.PENDING || !isExpiredAt(now)) {
            return false;
        }
        status = InvitationStatus.EXPIRED;
        return true;
    }

    public UUID id() {
        return id;
    }

    public UUID teacherUserId() {
        return teacherUserId;
    }

    public InvitationEmail studentEmail() {
        return studentEmail;
    }

    public UUID studentUserId() {
        return studentUserId;
    }

    public Instant attachedAt() {
        return attachedAt;
    }

    public InvitationStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant respondedAt() {
        return respondedAt;
    }

    private void respond(UUID actorStudentUserId, Instant now, InvitationStatus newStatus, String operation) {
        Objects.requireNonNull(actorStudentUserId, "Actor student user id must not be null");
        Objects.requireNonNull(now, "Response time must not be null");
        if (!actorStudentUserId.equals(studentUserId)) {
            throw new InvitationOwnershipException(id);
        }
        requirePending(operation);
        requireNotExpired(now);
        if (now.isBefore(attachedAt)) {
            throw new InvalidInvitationStateException(id, status, operation + " before attachment");
        }

        status = newStatus;
        respondedAt = now;
    }

    private void requirePending(String operation) {
        if (status != InvitationStatus.PENDING) {
            throw new InvalidInvitationStateException(id, status, operation);
        }
    }

    private void requireNotExpired(Instant now) {
        if (isExpiredAt(now)) {
            throw new InvitationExpiredException(id, expiresAt);
        }
    }

    private void validateState() {
        if (!expiresAt.equals(createdAt.plus(VALIDITY))
                || (studentUserId == null) != (attachedAt == null)
                || (attachedAt != null && (attachedAt.isBefore(createdAt) || !attachedAt.isBefore(expiresAt)))) {
            throw new InvalidInvitationStateException(id, status, "restore invalid timestamps or attachment");
        }
        if (studentUserId != null && teacherUserId.equals(studentUserId)) {
            throw new SelfInvitationException("Teacher cannot invite themselves");
        }
        if (status == InvitationStatus.ACCEPTED || status == InvitationStatus.REJECTED) {
            if (respondedAt == null || attachedAt == null || respondedAt.isBefore(attachedAt)
                    || !respondedAt.isBefore(expiresAt)) {
                throw new InvalidInvitationStateException(id, status, "restore invalid response");
            }
        } else if (respondedAt != null) {
            throw new InvalidInvitationStateException(id, status, "restore unexpected response");
        }
    }
}
