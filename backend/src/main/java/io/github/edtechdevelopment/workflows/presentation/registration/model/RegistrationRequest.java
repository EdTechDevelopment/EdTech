package io.github.edtechdevelopment.workflows.presentation.registration.model;

import io.github.edtechdevelopment.identity.api.model.UserRoleView;
import io.github.edtechdevelopment.workflows.presentation.profile.StudentProfileRequest;
import io.github.edtechdevelopment.workflows.presentation.profile.TeacherProfileRequest;

import jakarta.validation.Valid;
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

public record RegistrationRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull @Size(min = 8, max = 72) @Pattern(regexp = "[!-~]+") String password,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotNull LocalDate birthDate,
        @NotEmpty @Size(max = 2) @UniqueElements List<@NotNull UserRoleView> roles,
        @Valid TeacherProfileRequest teacherProfile,
        @Valid StudentProfileRequest studentProfile
) {

    public RegistrationRequest {
        email = strip(email);
        firstName = strip(firstName);
        lastName = strip(lastName);
        roles = roles == null ? null : Collections.unmodifiableList(new ArrayList<>(roles));
    }

    @Override
    public String toString() {
        return "RegistrationRequest[PROTECTED]";
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }
}

