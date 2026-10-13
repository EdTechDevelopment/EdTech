package io.github.edtechdevelopment.tutoring.presentation.profile.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Set;

public record UpdateStudentProfileRequest(@NotBlank String displayName, @NotBlank String contactEmail,
        @NotNull List<@NotNull String> contactDetails, @NotNull Set<@NotNull String> subjectCodes,
        String photoUrl) {
        }
