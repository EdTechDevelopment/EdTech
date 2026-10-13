package io.github.edtechdevelopment.tutoring.application.relationship.model.query;

import java.util.Objects;
import java.util.UUID;

public record ListStudentTeachersQuery(UUID studentUserId, String cursor, int limit) {

    public ListStudentTeachersQuery {
        Objects.requireNonNull(studentUserId, "Student user id must not be null");
        ListTeacherStudentsQuery.validatePage(cursor, limit);
    }
}
