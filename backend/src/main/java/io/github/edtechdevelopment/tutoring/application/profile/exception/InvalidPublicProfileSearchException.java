package io.github.edtechdevelopment.tutoring.application.profile.exception;

import java.util.Set;

public final class InvalidPublicProfileSearchException extends RuntimeException {

    private final Set<String> invalidFields;

    public InvalidPublicProfileSearchException(String field) {
        super("Public profile search has an invalid filter");
        this.invalidFields = Set.of(field);
    }

    public Set<String> invalidFields() {
        return invalidFields;
    }
}
