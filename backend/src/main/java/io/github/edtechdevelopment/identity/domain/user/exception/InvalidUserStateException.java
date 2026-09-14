package io.github.edtechdevelopment.identity.domain.user.exception;

public final class InvalidUserStateException extends RuntimeException {

    public InvalidUserStateException(String message) {
        super(message);
    }
}
