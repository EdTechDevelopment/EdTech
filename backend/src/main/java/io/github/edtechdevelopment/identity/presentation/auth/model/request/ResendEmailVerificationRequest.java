package io.github.edtechdevelopment.identity.presentation.auth.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResendEmailVerificationRequest(
        @NotBlank(message = "Email must not be blank")
        @Email(message = "Email must have a valid format")
        @Size(max = 254, message = "Email must not exceed 254 characters")
        String email
) {

    public ResendEmailVerificationRequest {
        email = email == null ? null : email.strip();
    }

    @Override
    public String toString() {
        return "ResendEmailVerificationRequest[PROTECTED]";
    }
}
