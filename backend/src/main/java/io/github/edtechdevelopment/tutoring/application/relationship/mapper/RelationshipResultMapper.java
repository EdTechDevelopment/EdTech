package io.github.edtechdevelopment.tutoring.application.relationship.mapper;

import io.github.edtechdevelopment.tutoring.api.model.profile.StudentProfileView;
import io.github.edtechdevelopment.tutoring.api.model.profile.TeacherProfileView;
import io.github.edtechdevelopment.tutoring.application.relationship.model.result.StudentRelationshipResult;
import io.github.edtechdevelopment.tutoring.application.relationship.model.result.TeacherRelationshipResult;

import java.time.Instant;
import java.util.Objects;

public final class RelationshipResultMapper {

    public StudentRelationshipResult toStudentResult(Instant linkedAt, StudentProfileView student) {
        return new StudentRelationshipResult(linkedAt, Objects.requireNonNull(student, "Student profile must not be null"));
    }

    public TeacherRelationshipResult toTeacherResult(Instant linkedAt, TeacherProfileView teacher) {
        return new TeacherRelationshipResult(linkedAt, Objects.requireNonNull(teacher, "Teacher profile must not be null"));
    }
}
