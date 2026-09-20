package io.github.edtechdevelopment.identity.domain.user.exception;

public final class InvalidUserDataException extends RuntimeException {

    public InvalidUserDataException(String message) {
        super(message);
    }
}
