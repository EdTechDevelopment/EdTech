package io.github.edtechdevelopment.tutoring.application.relationship.model.result;

import io.github.edtechdevelopment.tutoring.api.model.profile.TeacherProfileView;

import java.time.Instant;
import java.util.Objects;

public record TeacherRelationshipResult(Instant linkedAt, TeacherProfileView teacher) {

    public TeacherRelationshipResult {
        Objects.requireNonNull(linkedAt, "Link time must not be null");
        Objects.requireNonNull(teacher, "Teacher profile must not be null");
    }
}
