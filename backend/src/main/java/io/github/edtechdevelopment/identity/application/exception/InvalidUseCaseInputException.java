package io.github.edtechdevelopment.identity.application.exception;

public class InvalidUseCaseInputException extends RuntimeException {

    public InvalidUseCaseInputException(String message) {
        super(message);
    }

    public InvalidUseCaseInputException(String message, Throwable cause) {
        super(message, cause);
    }
}
