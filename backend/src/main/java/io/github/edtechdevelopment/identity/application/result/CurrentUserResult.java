package io.github.edtechdevelopment.identity.application.result;

import io.github.edtechdevelopment.identity.domain.user.model.UserRole;
import io.github.edtechdevelopment.identity.domain.user.model.UserStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record CurrentUserResult(
        UUID id,
        String email,
        String pendingEmail,
        String firstName,
        String lastName,
        LocalDate birthDate,
        Set<UserRole> roles,
        UserStatus status,
        Instant emailVerifiedAt,
        Instant createdAt,
        Instant updatedAt
) {

    public CurrentUserResult {
        roles = Set.copyOf(roles);
    }
}
