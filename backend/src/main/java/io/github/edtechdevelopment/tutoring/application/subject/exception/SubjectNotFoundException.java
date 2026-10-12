package io.github.edtechdevelopment.tutoring.application.subject.exception;

public final class SubjectNotFoundException extends RuntimeException {

    public SubjectNotFoundException() {
        super("Subject catalog is empty");
    }
}
