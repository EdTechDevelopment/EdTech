package io.github.edtechdevelopment.tutoring.application.relationship.model.result;

import io.github.edtechdevelopment.tutoring.api.model.profile.StudentProfileView;

import java.time.Instant;
import java.util.Objects;

public record StudentRelationshipResult(Instant linkedAt, StudentProfileView student) {

    public StudentRelationshipResult {
        Objects.requireNonNull(linkedAt, "Link time must not be null");
        Objects.requireNonNull(student, "Student profile must not be null");
    }
}
