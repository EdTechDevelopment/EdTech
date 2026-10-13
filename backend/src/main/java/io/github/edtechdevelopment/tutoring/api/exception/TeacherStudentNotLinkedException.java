package io.github.edtechdevelopment.tutoring.api.exception;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class TeacherStudentNotLinkedException extends RuntimeException {

    private final UUID teacherUserId;
    private final Set<UUID> missingStudentUserIds;

    public TeacherStudentNotLinkedException(UUID teacherUserId, Set<UUID> missingStudentUserIds) {
        super("Teacher and student are not linked");
        this.teacherUserId = Objects.requireNonNull(teacherUserId, "Teacher user id must not be null");
        this.missingStudentUserIds = Set.copyOf(Objects.requireNonNull(
                missingStudentUserIds, "Missing student user ids must not be null"
        ));
    }

    public UUID teacherUserId() {
        return teacherUserId;
    }

    public Set<UUID> missingStudentUserIds() {
        return missingStudentUserIds;
    }
}
