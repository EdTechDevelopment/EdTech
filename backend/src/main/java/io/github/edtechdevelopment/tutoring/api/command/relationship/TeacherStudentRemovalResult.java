package io.github.edtechdevelopment.tutoring.api.command.relationship;

import java.util.Objects;
import java.util.UUID;

public record TeacherStudentRemovalResult(UUID operationId, UUID teacherUserId, UUID studentUserId) {

    public TeacherStudentRemovalResult {
        Objects.requireNonNull(operationId, "Operation id must not be null");
        Objects.requireNonNull(teacherUserId, "Teacher user id must not be null");
        Objects.requireNonNull(studentUserId, "Student user id must not be null");
    }
}
