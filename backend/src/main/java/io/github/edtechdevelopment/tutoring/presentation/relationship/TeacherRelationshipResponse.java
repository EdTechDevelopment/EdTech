package io.github.edtechdevelopment.tutoring.presentation.relationship;

import io.github.edtechdevelopment.tutoring.presentation.profile.model.TeacherProfileResponse;

import java.time.Instant;

public record TeacherRelationshipResponse(Instant linkedAt, TeacherProfileResponse teacher) { }
