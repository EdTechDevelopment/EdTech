package io.github.edtechdevelopment.tutoring.application.relationship.model.result;

import java.util.List;
import java.util.Objects;

public record TeacherRelationshipPageResult(List<TeacherRelationshipResult> items, String nextCursor) {

    public TeacherRelationshipPageResult {
        items = List.copyOf(Objects.requireNonNull(items, "Relationship items must not be null"));
    }
}
