package io.github.edtechdevelopment.identity.domain.verification.exception;

public final class InvalidEmailVerificationException extends RuntimeException {

    public InvalidEmailVerificationException(String message) {
        super(message);
    }
}
