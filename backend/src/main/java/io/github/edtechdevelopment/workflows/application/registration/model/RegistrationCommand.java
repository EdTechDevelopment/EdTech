package io.github.edtechdevelopment.workflows.application.registration.model;

import io.github.edtechdevelopment.identity.api.model.UserRoleView;
import io.github.edtechdevelopment.tutoring.api.model.profile.input.StudentProfileData;
import io.github.edtechdevelopment.tutoring.api.model.profile.input.TeacherProfileData;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record RegistrationCommand(
        UUID operationId,
        String email,
        String rawPassword,
        String firstName,
        String lastName,
        LocalDate birthDate,
        Set<UserRoleView> roles,
        TeacherProfileData teacherProfile,
        StudentProfileData studentProfile
) {

    public RegistrationCommand {
        Objects.requireNonNull(operationId, "Operation id must not be null");
        Objects.requireNonNull(email, "Email must not be null");
        Objects.requireNonNull(rawPassword, "Password must not be null");
        Objects.requireNonNull(firstName, "First name must not be null");
        Objects.requireNonNull(lastName, "Last name must not be null");
        Objects.requireNonNull(birthDate, "Birth date must not be null");
        roles = Set.copyOf(Objects.requireNonNull(roles, "Roles must not be null"));
    }

    @Override
    public String toString() {
        return "RegistrationCommand[PROTECTED]";
    }
}

