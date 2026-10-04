package io.github.edtechdevelopment.identity.presentation.account.model.response;

import io.github.edtechdevelopment.identity.api.model.UserRoleView;
import io.github.edtechdevelopment.identity.api.model.UserStatusView;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
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

    public UserResponse {
        roles = Set.copyOf(roles);
    }
}
