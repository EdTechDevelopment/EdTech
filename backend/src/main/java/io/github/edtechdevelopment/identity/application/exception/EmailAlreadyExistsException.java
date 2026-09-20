package io.github.edtechdevelopment.identity.application.exception;

public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException() {
        super("Email is already registered");
    }

    public EmailAlreadyExistsException(Throwable cause) {
        super("Email is already registered", cause);
    }
}
