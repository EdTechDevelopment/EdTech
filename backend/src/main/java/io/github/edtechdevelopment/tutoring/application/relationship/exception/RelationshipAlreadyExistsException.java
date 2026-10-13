package io.github.edtechdevelopment.tutoring.application.relationship.exception;

public class RelationshipAlreadyExistsException extends RuntimeException {

    public RelationshipAlreadyExistsException() {
        super("Teacher and student are already linked");
    }
}
