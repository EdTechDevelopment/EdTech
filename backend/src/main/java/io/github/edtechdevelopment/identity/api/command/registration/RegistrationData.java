package io.github.edtechdevelopment.identity.api.command.registration;

import io.github.edtechdevelopment.identity.api.model.UserRoleView;

import java.time.LocalDate;
import java.util.Set;

public record RegistrationData(
        String email,
        String rawPassword,
        String firstName,
        String lastName,
        LocalDate birthDate,
        Set<UserRoleView> roles
) {

    public RegistrationData {
        roles = roles == null ? null : Set.copyOf(roles);
    }

    @Override
    public String toString() {
        return "RegistrationData[PROTECTED]";
    }
}
