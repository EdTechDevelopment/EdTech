package io.github.edtechdevelopment.tutoring.application.relationship.port.out;

import io.github.edtechdevelopment.tutoring.application.relationship.model.query.RelationshipPageCursor;
import io.github.edtechdevelopment.tutoring.domain.relationship.model.TeacherStudent;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface TeacherStudentRepository {

    boolean exists(UUID teacherUserId, UUID studentUserId);

    Set<UUID> findLinkedStudentUserIds(UUID teacherUserId, Set<UUID> studentUserIds);

    Optional<TeacherStudent> findForUpdate(UUID teacherUserId, UUID studentUserId);

    List<TeacherStudent> findByTeacher(UUID teacherUserId, RelationshipPageCursor after, int fetchLimit);

    List<TeacherStudent> findByStudent(UUID studentUserId, RelationshipPageCursor after, int fetchLimit);

    void insert(TeacherStudent relationship);

    void delete(TeacherStudent relationship);
}
