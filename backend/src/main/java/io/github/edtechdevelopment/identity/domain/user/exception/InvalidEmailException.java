package io.github.edtechdevelopment.identity.domain.user.exception;

public final class InvalidEmailException extends RuntimeException {

    public InvalidEmailException(String message) {
        super(message);
    }
}
