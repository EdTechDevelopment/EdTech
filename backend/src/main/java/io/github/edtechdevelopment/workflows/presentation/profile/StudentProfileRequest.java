package io.github.edtechdevelopment.workflows.presentation.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Set;

public record StudentProfileRequest(
        @NotBlank String displayName,
        @NotBlank String contactEmail,
        @NotNull List<@NotNull String> contactDetails,
        @NotNull Set<@NotNull String> subjectCodes,
        String photoUrl
) {
}

