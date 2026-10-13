package io.github.edtechdevelopment.tutoring.presentation.profile.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;
import java.util.Set;

public record UpdateTeacherProfileRequest(@NotBlank String displayName, @NotBlank String contactEmail,
        @NotNull List<@NotNull String> contactDetails, @NotEmpty Set<@NotNull String> subjectCodes,
        String description, String education, @PositiveOrZero Integer experienceYears, String city, String photoUrl) {
        }
