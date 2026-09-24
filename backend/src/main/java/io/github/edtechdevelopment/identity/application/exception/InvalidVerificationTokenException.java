package io.github.edtechdevelopment.identity.application.exception;

public class InvalidVerificationTokenException extends RuntimeException {

    public InvalidVerificationTokenException() {
        super("Verification token is invalid or expired");
    }

    public InvalidVerificationTokenException(Throwable cause) {
        super("Verification token is invalid or expired", cause);
    }
}
