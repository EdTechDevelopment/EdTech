package io.github.edtechdevelopment.tutoring.api.query;

import java.util.Set;
import java.util.UUID;

public interface TutoringRelationshipQuery {

    boolean areLinked(UUID teacherUserId, UUID studentUserId);

    void requireLinked(UUID teacherUserId, UUID studentUserId);

    void requireAllLinked(UUID teacherUserId, Set<UUID> studentUserIds);
}
