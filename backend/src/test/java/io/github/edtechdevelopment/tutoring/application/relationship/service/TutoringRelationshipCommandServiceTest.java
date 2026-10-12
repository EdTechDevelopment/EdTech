package io.github.edtechdevelopment.tutoring.application.relationship.service;

import io.github.edtechdevelopment.tutoring.api.command.relationship.RemoveTeacherStudentCommand;
import io.github.edtechdevelopment.tutoring.api.command.relationship.TeacherStudentRemovalResult;
import io.github.edtechdevelopment.tutoring.application.idempotency.model.OperationType;
import io.github.edtechdevelopment.tutoring.application.idempotency.model.PayloadFingerprint;
import io.github.edtechdevelopment.tutoring.application.idempotency.port.in.CommandIdempotency;
import io.github.edtechdevelopment.tutoring.application.relationship.port.out.TeacherStudentRepository;
import io.github.edtechdevelopment.tutoring.domain.relationship.model.TeacherStudent;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TutoringRelationshipCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");
    private static final UUID TEACHER_ID = UUID.randomUUID();
    private static final UUID STUDENT_ID = UUID.randomUUID();
    private static final UUID OPERATION_ID = UUID.randomUUID();

    private final TeacherStudentRepository relationships = mock(TeacherStudentRepository.class);
    private final CommandIdempotency idempotency = mock(CommandIdempotency.class);
    private final TutoringRelationshipCommandService service = new TutoringRelationshipCommandService(
            relationships, idempotency, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void removesRelationshipOnlyThroughTheTrustedCommand() {
        RemoveTeacherStudentCommand command =
                new RemoveTeacherStudentCommand(OPERATION_ID, TEACHER_ID, STUDENT_ID);
        TeacherStudent relationship = TeacherStudent.create(TEACHER_ID, STUDENT_ID, NOW.minusSeconds(60));
        PayloadFingerprint fingerprint = PayloadFingerprint.from(TEACHER_ID + "\n" + STUDENT_ID);

        when(idempotency.beginOrReplay(OPERATION_ID, TEACHER_ID, OperationType.REMOVE_TEACHER_STUDENT,
                fingerprint, TeacherStudentRemovalResult.class)).thenReturn(Optional.empty());
        when(relationships.findForUpdate(TEACHER_ID, STUDENT_ID)).thenReturn(Optional.of(relationship));

        TeacherStudentRemovalResult result = service.removeTeacherStudent(command);

        assertEquals(OPERATION_ID, result.operationId());
        assertEquals(TEACHER_ID, result.teacherUserId());
        assertEquals(STUDENT_ID, result.studentUserId());
        verify(relationships).delete(relationship);
        verify(idempotency).complete(OPERATION_ID, TEACHER_ID, OperationType.REMOVE_TEACHER_STUDENT,
                fingerprint, result, NOW);
    }
}
