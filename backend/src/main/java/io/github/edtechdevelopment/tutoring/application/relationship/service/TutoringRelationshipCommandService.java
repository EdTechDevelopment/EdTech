package io.github.edtechdevelopment.tutoring.application.relationship.service;

import io.github.edtechdevelopment.tutoring.api.command.relationship.RemoveTeacherStudentCommand;
import io.github.edtechdevelopment.tutoring.api.command.relationship.TeacherStudentRemovalResult;
import io.github.edtechdevelopment.tutoring.api.command.relationship.TutoringRelationshipCommands;
import io.github.edtechdevelopment.tutoring.api.exception.TeacherStudentNotLinkedException;
import io.github.edtechdevelopment.tutoring.application.idempotency.model.OperationType;
import io.github.edtechdevelopment.tutoring.application.idempotency.model.PayloadFingerprint;
import io.github.edtechdevelopment.tutoring.application.idempotency.port.in.CommandIdempotency;
import io.github.edtechdevelopment.tutoring.application.relationship.port.out.TeacherStudentRepository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class TutoringRelationshipCommandService implements TutoringRelationshipCommands {

    private static final OperationType OPERATION_TYPE = OperationType.REMOVE_TEACHER_STUDENT;

    private final TeacherStudentRepository relationships;
    private final CommandIdempotency idempotency;
    private final Clock clock;

    public TutoringRelationshipCommandService(
            TeacherStudentRepository relationships, CommandIdempotency idempotency, Clock clock) {

        this.relationships = Objects.requireNonNull(relationships, "Relationship repository must not be null");
        this.idempotency = Objects.requireNonNull(idempotency, "Command idempotency must not be null");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public TeacherStudentRemovalResult removeTeacherStudent(RemoveTeacherStudentCommand command) {
        Objects.requireNonNull(command, "Remove relationship command must not be null");
        PayloadFingerprint fingerprint = PayloadFingerprint.from(
                command.teacherUserId() + "\n" + command.studentUserId());
        Optional<TeacherStudentRemovalResult> replay = idempotency.beginOrReplay(
                command.operationId(), command.teacherUserId(), OPERATION_TYPE, fingerprint,
                TeacherStudentRemovalResult.class);
        if (replay.isPresent()) {
            return replay.get();
        }

        var relationship = relationships.findForUpdate(command.teacherUserId(), command.studentUserId())
                .orElseThrow(() -> new TeacherStudentNotLinkedException(
                        command.teacherUserId(), Set.of(command.studentUserId())));
        relationships.delete(relationship);

        TeacherStudentRemovalResult result = new TeacherStudentRemovalResult(
                command.operationId(), command.teacherUserId(), command.studentUserId());
        idempotency.complete(command.operationId(), command.teacherUserId(), OPERATION_TYPE,
                fingerprint, result, clock.instant());
        return result;
    }
}
