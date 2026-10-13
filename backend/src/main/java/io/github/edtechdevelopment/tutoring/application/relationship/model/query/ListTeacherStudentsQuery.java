package io.github.edtechdevelopment.tutoring.application.relationship.model.query;

import java.util.Objects;
import java.util.UUID;

public record ListTeacherStudentsQuery(UUID teacherUserId, String cursor, int limit) {

    public ListTeacherStudentsQuery {
        Objects.requireNonNull(teacherUserId, "Teacher user id must not be null");
        validatePage(cursor, limit);
    }

    static void validatePage(String cursor, int limit) {
        if (cursor != null && cursor.isBlank()) {
            throw new IllegalArgumentException("Cursor must not be blank");
        }
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Limit must be between 1 and 100");
        }
    }
}
