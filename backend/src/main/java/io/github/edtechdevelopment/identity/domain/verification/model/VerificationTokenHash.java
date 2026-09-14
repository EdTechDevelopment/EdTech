package io.github.edtechdevelopment.identity.domain.verification.model;

import io.github.edtechdevelopment.identity.domain.verification.exception.InvalidEmailVerificationException;

public record VerificationTokenHash(String value) {

    private static final int MAX_LENGTH = 64;

    public VerificationTokenHash {
        if (value == null) {
            throw new InvalidEmailVerificationException("Verification token hash must not be null");
        }
        if (value.isBlank()) {
            throw new InvalidEmailVerificationException("Verification token hash must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new InvalidEmailVerificationException(
                    "Verification token hash must not exceed 64 characters"
            );
        }
        if (value.chars().anyMatch(Character::isWhitespace)) {
            throw new InvalidEmailVerificationException(
                    "Verification token hash must not contain whitespace"
            );
        }
    }

    @Override
    public String toString() {
        return "VerificationTokenHash[PROTECTED]";
    }
}
