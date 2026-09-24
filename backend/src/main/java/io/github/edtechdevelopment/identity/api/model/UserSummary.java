package io.github.edtechdevelopment.identity.api.model;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record UserSummary(
        UUID id,
        String email,
        String firstName,
        String lastName,
        Set<UserRoleView> roles,
        UserStatusView status
) {

    public UserSummary {
        Objects.requireNonNull(id, "User id must not be null");
        requireText(email, "Email must not be blank");
        requireText(firstName, "First name must not be blank");
        requireText(lastName, "Last name must not be blank");
        Objects.requireNonNull(status, "User status must not be null");

        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("User roles must not be empty");
        }
        if (roles.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("User roles must not contain null");
        }
        roles = Set.copyOf(roles);
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
