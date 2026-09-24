package io.github.edtechdevelopment.identity.presentation.account.model.request;

import io.github.edtechdevelopment.identity.presentation.account.validation.ValidAccountUpdate;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

@ValidAccountUpdate
public record UpdateCurrentUserRequest(
        @Email(message = "Email must have a valid format")
        @Size(min = 1, max = 254, message = "Email must contain from 1 to 254 characters")
        String email,

        @Size(min = 1, max = 100, message = "First name must contain from 1 to 100 characters")
        String firstName,

        @Size(min = 1, max = 100, message = "Last name must contain from 1 to 100 characters")
        String lastName
) {

    public UpdateCurrentUserRequest {
        email = stripNullable(email);
        firstName = stripNullable(firstName);
        lastName = stripNullable(lastName);
    }

    @Override
    public String toString() {
        return "UpdateCurrentUserRequest[PROTECTED]";
    }

    private static String stripNullable(String value) {
        return value == null ? null : value.strip();
    }
}
