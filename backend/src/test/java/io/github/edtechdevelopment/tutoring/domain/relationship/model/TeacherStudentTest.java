package io.github.edtechdevelopment.tutoring.domain.relationship.model;

import io.github.edtechdevelopment.tutoring.domain.relationship.exception.SelfRelationshipException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TeacherStudentTest {

    @Test
    void identifiesRelationshipByTeacherAndStudent() {
        UUID teacherId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-01T10:00:00Z");

        TeacherStudent relationship = TeacherStudent.create(teacherId, studentId, now);

        assertEquals(teacherId, relationship.teacherUserId());
        assertEquals(studentId, relationship.studentUserId());
        assertEquals(now, relationship.createdAt());
    }

    @Test
    void rejectsSelfRelationship() {
        UUID userId = UUID.randomUUID();

        assertThrows(SelfRelationshipException.class,
                () -> TeacherStudent.create(userId, userId, Instant.now()));
    }
}
