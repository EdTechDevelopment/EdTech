package io.github.edtechdevelopment.identity.domain.user.model;

import io.github.edtechdevelopment.identity.domain.user.exception.InvalidUserDataException;

public record PasswordHash(String value) {

    public PasswordHash {
        if (value == null || value.isBlank()) {
            throw new InvalidUserDataException("Password hash must not be blank");
        }
    }

    @Override
    public String toString() {
        return "PasswordHash[PROTECTED]";
    }
}
