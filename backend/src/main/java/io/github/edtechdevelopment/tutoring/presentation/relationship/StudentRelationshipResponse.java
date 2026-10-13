package io.github.edtechdevelopment.tutoring.presentation.relationship;

import io.github.edtechdevelopment.tutoring.presentation.profile.model.StudentProfileResponse;

import java.time.Instant;

public record StudentRelationshipResponse(Instant linkedAt, StudentProfileResponse student) { }
