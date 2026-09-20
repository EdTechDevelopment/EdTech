package io.github.edtechdevelopment.identity.domain.verification.model;

import io.github.edtechdevelopment.identity.domain.verification.exception.InvalidEmailVerificationException;

public record VerificationTokenHash(String value) {

    private static final int SHA_256_HEX_LENGTH = 64;

    public VerificationTokenHash {
        if (value == null) {
            throw new InvalidEmailVerificationException("Verification token hash must not be null");
        }
        if (value.isBlank()) {
            throw new InvalidEmailVerificationException("Verification token hash must not be blank");
        }
        if (value.length() != SHA_256_HEX_LENGTH) {
            throw new InvalidEmailVerificationException(
                    "Verification token hash must contain exactly 64 characters"
            );
        }
        if (!value.matches("[0-9a-f]{64}")) {
            throw new InvalidEmailVerificationException(
                    "Verification token hash must use lowercase hexadecimal format"
            );
        }
    }

    @Override
    public String toString() {
        return "VerificationTokenHash[PROTECTED]";
    }
}
