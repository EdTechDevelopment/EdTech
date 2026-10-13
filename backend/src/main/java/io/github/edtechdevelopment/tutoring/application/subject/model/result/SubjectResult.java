package io.github.edtechdevelopment.tutoring.application.subject.model.result;

import java.util.Objects;

public record SubjectResult(String subjectCode, String name) {

    public SubjectResult {
        Objects.requireNonNull(subjectCode, "Subject code must not be null");
        Objects.requireNonNull(name, "Subject name must not be null");
    }
}
