package io.github.edtechdevelopment.identity.domain.user.model;

import io.github.edtechdevelopment.identity.domain.user.exception.InvalidEmailException;

import java.util.Locale;

public record Email(String value) {

    private static final int MAX_LENGTH = 254;

    public Email {
        if (value == null) {
            throw new InvalidEmailException("Email must not be null");
        }

        String normalizedValue = value.strip().toLowerCase(Locale.ROOT);
        validate(normalizedValue);
        value = normalizedValue;
    }

    private static void validate(String value) {
        if (value.isEmpty()) {
            throw new InvalidEmailException("Email must not be empty");
        }

        if (value.length() > MAX_LENGTH) {
            throw new InvalidEmailException("Email must not exceed 254 characters");
        }

        if (value.chars().anyMatch(Character::isWhitespace)) {
            throw new InvalidEmailException("Email must not contain whitespace");
        }

        int separatorIndex = value.indexOf('@');
        boolean hasSingleSeparator = separatorIndex == value.lastIndexOf('@');
        boolean hasLocalPart = separatorIndex > 0;
        boolean hasDomainPart = separatorIndex < value.length() - 1;

        if (!hasSingleSeparator || !hasLocalPart || !hasDomainPart) {
            throw new InvalidEmailException("Email must contain one local part and one domain part");
        }
    }
}
