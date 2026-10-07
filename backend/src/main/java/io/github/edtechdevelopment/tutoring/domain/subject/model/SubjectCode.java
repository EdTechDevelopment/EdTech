package io.github.edtechdevelopment.tutoring.domain.subject.model;

import io.github.edtechdevelopment.tutoring.domain.subject.exception.InvalidSubjectCodeException;

import java.util.regex.Pattern;

public record SubjectCode(String value) {

    private static final int MAX_LENGTH = 32;
    private static final Pattern FORMAT = Pattern.compile("[A-Z][A-Z0-9_]*");

    public SubjectCode {
        if (value == null || value.length() > MAX_LENGTH || !FORMAT.matcher(value).matches()) {
            throw new InvalidSubjectCodeException("Subject code must be 1-32 uppercase letters, digits or underscores and start with a letter");
        }
    }
}
