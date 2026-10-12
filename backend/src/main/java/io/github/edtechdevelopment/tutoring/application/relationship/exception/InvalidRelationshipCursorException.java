package io.github.edtechdevelopment.tutoring.application.relationship.exception;

public final class InvalidRelationshipCursorException extends RuntimeException {

    public InvalidRelationshipCursorException() {
        super("Relationship cursor is invalid");
    }
}
