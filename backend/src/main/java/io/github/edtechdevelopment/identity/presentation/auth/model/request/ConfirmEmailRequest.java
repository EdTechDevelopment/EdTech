package io.github.edtechdevelopment.identity.presentation.auth.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ConfirmEmailRequest(
        @NotBlank(message = "Verification token must not be blank")
        @Size(
                min = 20,
                max = 512,
                message = "Verification token must contain from 20 to 512 characters"
        )
        @Pattern(
                regexp = "[A-Za-z0-9_-]+",
                message = "Verification token must have a valid URL-safe format"
        )
        String token
) {

    @Override
    public String toString() {
        return "ConfirmEmailRequest[PROTECTED]";
    }
}
