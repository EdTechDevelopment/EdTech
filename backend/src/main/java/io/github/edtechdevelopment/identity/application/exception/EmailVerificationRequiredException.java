package io.github.edtechdevelopment.identity.application.exception;

public class EmailVerificationRequiredException extends RuntimeException {

    public EmailVerificationRequiredException() {
        super("Email verification is required");
    }
}
