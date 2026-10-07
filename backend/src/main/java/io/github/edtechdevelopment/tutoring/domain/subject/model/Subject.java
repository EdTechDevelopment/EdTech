package io.github.edtechdevelopment.tutoring.domain.subject.model;

import io.github.edtechdevelopment.tutoring.domain.subject.exception.InvalidSubjectNameException;

import java.util.Objects;

public final class Subject {

    private final SubjectCode code;
    private final String name;

    public Subject(SubjectCode code, String name) {
        this.code = Objects.requireNonNull(code, "Subject code must not be null");
        if (name == null || name.isBlank()) {
            throw new InvalidSubjectNameException("Subject name must not be blank");
        }
        this.name = name.strip();
    }

    public SubjectCode code() {
        return code;
    }

    public String name() {
        return name;
    }
}
