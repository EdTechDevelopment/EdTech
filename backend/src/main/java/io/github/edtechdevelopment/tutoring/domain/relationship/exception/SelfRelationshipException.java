package io.github.edtechdevelopment.tutoring.domain.relationship.exception;

public final class SelfRelationshipException extends RuntimeException {

    public SelfRelationshipException(String message) {
        super(message);
    }
}
