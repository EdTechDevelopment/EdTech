package io.github.edtechdevelopment.workflows.presentation.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;
import java.util.Set;

public record TeacherProfileRequest(
        @NotBlank String displayName,
        @NotBlank String contactEmail,
        @NotNull List<@NotNull String> contactDetails,
        @NotEmpty Set<@NotNull String> subjectCodes,
        String description,
        String education,
        @PositiveOrZero Integer experienceYears,
        String city,
        String photoUrl
) {
}

