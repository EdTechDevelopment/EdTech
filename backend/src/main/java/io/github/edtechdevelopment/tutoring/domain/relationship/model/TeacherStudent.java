package io.github.edtechdevelopment.tutoring.domain.relationship.model;

import io.github.edtechdevelopment.tutoring.domain.relationship.exception.SelfRelationshipException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class TeacherStudent {

    private final UUID teacherUserId;
    private final UUID studentUserId;
    private final Instant createdAt;

    private TeacherStudent(UUID teacherUserId, UUID studentUserId, Instant createdAt) {
        this.teacherUserId = Objects.requireNonNull(teacherUserId, "Teacher user id must not be null");
        this.studentUserId = Objects.requireNonNull(studentUserId, "Student user id must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "Creation time must not be null");
        if (teacherUserId.equals(studentUserId)) {
            throw new SelfRelationshipException("Teacher and student must be different users");
        }
    }

    public static TeacherStudent create(UUID teacherUserId, UUID studentUserId, Instant createdAt) {
        return new TeacherStudent(teacherUserId, studentUserId, createdAt);
    }

    public static TeacherStudent reconstitute(UUID teacherUserId, UUID studentUserId, Instant createdAt) {
        return new TeacherStudent(teacherUserId, studentUserId, createdAt);
    }

    public UUID teacherUserId() {
        return teacherUserId;
    }

    public UUID studentUserId() {
        return studentUserId;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
