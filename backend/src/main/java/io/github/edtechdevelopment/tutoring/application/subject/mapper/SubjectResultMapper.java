package io.github.edtechdevelopment.tutoring.application.subject.mapper;

import io.github.edtechdevelopment.tutoring.application.subject.model.result.SubjectResult;
import io.github.edtechdevelopment.tutoring.domain.subject.model.Subject;

import java.util.Objects;

public final class SubjectResultMapper {

    public SubjectResult toResult(Subject subject) {
        Objects.requireNonNull(subject, "Subject must not be null");
        return new SubjectResult(subject.code().value(), subject.name());
    }
}
