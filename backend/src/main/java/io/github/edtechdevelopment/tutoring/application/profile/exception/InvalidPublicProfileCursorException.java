package io.github.edtechdevelopment.tutoring.application.profile.exception;

public final class InvalidPublicProfileCursorException extends RuntimeException {

    public InvalidPublicProfileCursorException() {
        super("Public profile cursor is invalid or does not match the search");
    }
}
