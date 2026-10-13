package io.github.edtechdevelopment.identity.api.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record OwnAccountView(
        UUID id,
        String email,
        String pendingEmail,
        String firstName,
        String lastName,
        LocalDate birthDate,
        Set<UserRoleView> roles,
        UserStatusView status,
        Instant emailVerifiedAt,
        Instant createdAt,
        Instant updatedAt
) {

    public OwnAccountView {
        Objects.requireNonNull(id, "Account id must not be null");
        Objects.requireNonNull(email, "Account email must not be null");
        Objects.requireNonNull(firstName, "First name must not be null");
        Objects.requireNonNull(lastName, "Last name must not be null");
        Objects.requireNonNull(birthDate, "Birth date must not be null");
        Objects.requireNonNull(status, "Account status must not be null");
        Objects.requireNonNull(createdAt, "Creation time must not be null");
        Objects.requireNonNull(updatedAt, "Update time must not be null");
        roles = Set.copyOf(Objects.requireNonNull(roles, "Roles must not be null"));
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("Account roles must not be empty");
        }
    }

    @Override
    public String toString() {
        return "OwnAccountView[PROTECTED]";
    }
}

