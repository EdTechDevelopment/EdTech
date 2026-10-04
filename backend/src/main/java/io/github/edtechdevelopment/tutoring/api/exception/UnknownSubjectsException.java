package io.github.edtechdevelopment.tutoring.api.exception;

import java.util.Objects;
import java.util.Set;

public final class UnknownSubjectsException extends RuntimeException {

    private final Set<String> unknownCodes;

    public UnknownSubjectsException(Set<String> unknownCodes) {
        super("One or more subjects do not exist");
        this.unknownCodes = Set.copyOf(Objects.requireNonNull(unknownCodes, "Unknown codes must not be null"));
    }

    public Set<String> unknownCodes() {
        return unknownCodes;
    }
}
