package io.github.edtechdevelopment.tutoring.application.relationship.model.result;

import java.util.List;
import java.util.Objects;

public record StudentRelationshipPageResult(List<StudentRelationshipResult> items, String nextCursor) {

    public StudentRelationshipPageResult {
        items = List.copyOf(Objects.requireNonNull(items, "Relationship items must not be null"));
    }
}
