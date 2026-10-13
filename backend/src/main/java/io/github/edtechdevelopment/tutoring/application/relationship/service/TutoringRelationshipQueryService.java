package io.github.edtechdevelopment.tutoring.application.relationship.service;

import io.github.edtechdevelopment.tutoring.api.exception.TeacherStudentNotLinkedException;
import io.github.edtechdevelopment.tutoring.api.query.TutoringRelationshipQuery;
import io.github.edtechdevelopment.tutoring.application.relationship.port.out.TeacherStudentRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Transactional(readOnly = true)
public class TutoringRelationshipQueryService implements TutoringRelationshipQuery {

    private final TeacherStudentRepository relationships;

    public TutoringRelationshipQueryService(TeacherStudentRepository relationships) {
        this.relationships = Objects.requireNonNull(relationships, "Relationship repository must not be null");
    }

    @Override
    public boolean areLinked(UUID teacherUserId, UUID studentUserId) {
        Objects.requireNonNull(teacherUserId, "Teacher user id must not be null");
        Objects.requireNonNull(studentUserId, "Student user id must not be null");
        return relationships.exists(teacherUserId, studentUserId);
    }

    @Override
    public void requireLinked(UUID teacherUserId, UUID studentUserId) {
        Objects.requireNonNull(teacherUserId, "Teacher user id must not be null");
        Objects.requireNonNull(studentUserId, "Student user id must not be null");
        if (!relationships.exists(teacherUserId, studentUserId)) {
            throw new TeacherStudentNotLinkedException(teacherUserId, Set.of(studentUserId));
        }
    }

    @Override
    public void requireAllLinked(UUID teacherUserId, Set<UUID> studentUserIds) {
        Objects.requireNonNull(teacherUserId, "Teacher user id must not be null");
        Set<UUID> requestedIds = validateIds(studentUserIds);
        if (requestedIds.isEmpty()) {
            return;
        }

        Set<UUID> linkedIds = relationships.findLinkedStudentUserIds(teacherUserId, requestedIds);
        Set<UUID> missingIds = new HashSet<>(requestedIds);
        missingIds.removeAll(linkedIds);
        if (!missingIds.isEmpty()) {
            throw new TeacherStudentNotLinkedException(teacherUserId, missingIds);
        }
    }

    private static Set<UUID> validateIds(Set<UUID> ids) {
        if (ids == null || ids.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Student user ids must not be null or contain null");
        }
        return Set.copyOf(ids);
    }
}
