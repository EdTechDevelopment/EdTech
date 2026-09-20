package io.github.edtechdevelopment.identity.infrastructure.persistence.exception;

public class InvalidPersistenceDataException extends RuntimeException {

    public InvalidPersistenceDataException(String message) {
        super(message);
    }

    public InvalidPersistenceDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
