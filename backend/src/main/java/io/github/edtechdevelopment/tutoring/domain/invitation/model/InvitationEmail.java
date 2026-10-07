package io.github.edtechdevelopment.tutoring.domain.invitation.model;

import io.github.edtechdevelopment.tutoring.domain.invitation.exception.InvalidInvitationEmailException;

import java.util.Locale;

public record InvitationEmail(String value) {

    private static final int MAX_LENGTH = 254;

    public InvitationEmail {
        if (value == null) {
            throw new InvalidInvitationEmailException("Invitation email must not be null");
        }

        value = value.strip().toLowerCase(Locale.ROOT);
        int separator = value.indexOf('@');
        if (value.isEmpty() || value.length() > MAX_LENGTH || value.chars().anyMatch(Character::isWhitespace)
                || separator < 1 || separator != value.lastIndexOf('@') || separator == value.length() - 1) {
            throw new InvalidInvitationEmailException("Invitation email must contain one local part and one domain part without whitespace");
        }
    }
}
