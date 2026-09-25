package io.github.edtechdevelopment.identity.presentation.auth.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginRequest(
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
        String password
) {

    public LoginRequest {
        email = email == null ? null : email.strip();
    }

    @Override
    public String toString() {
        return "LoginRequest[PROTECTED]";
    }
}
