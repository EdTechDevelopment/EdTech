package io.github.edtechdevelopment.identity.presentation.auth.model.request;

import io.github.edtechdevelopment.identity.application.command.account.RegistrationRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.UniqueElements;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record RegisterRequest(
        @NotBlank(message = "Email must not be blank")
        @Email(message = "Email must have a valid format")
        @Size(max = 254, message = "Email must not exceed 254 characters")
        String email,

        @NotNull(message = "Password must not be null")
        @Size(min = 8, max = 72, message = "Password must contain from 8 to 72 characters")
        @Pattern(
                regexp = "[!-~]+",
                message = "Password must contain only printable ASCII characters without spaces"
        )
        String password,

        @NotBlank(message = "First name must not be blank")
        @Size(max = 100, message = "First name must not exceed 100 characters")
        String firstName,

        @NotBlank(message = "Last name must not be blank")
        @Size(max = 100, message = "Last name must not exceed 100 characters")
        String lastName,

        @NotNull(message = "Birth date must not be null")
        LocalDate birthDate,

        @NotEmpty(message = "At least one registration role must be provided")
        @Size(max = 2, message = "No more than two registration roles may be provided")
        @UniqueElements(message = "Registration roles must not contain duplicates")
        List<@NotNull(message = "Registration role must not be null") RegistrationRole> roles
) {

    public RegisterRequest {
        email = stripNullable(email);
        firstName = stripNullable(firstName);
        lastName = stripNullable(lastName);
        roles = roles == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(roles));
    }

    @Override
    public String toString() {
        return "RegisterRequest[PROTECTED]";
    }

    private static String stripNullable(String value) {
        return value == null ? null : value.strip();
    }
}
