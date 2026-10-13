package io.github.edtechdevelopment.tutoring.infrastructure.persistence;

public final class InvalidPersistenceDataException extends RuntimeException {
    public InvalidPersistenceDataException(String message) {
        super(message);
    }
    public InvalidPersistenceDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
