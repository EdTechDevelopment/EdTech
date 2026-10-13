package io.github.edtechdevelopment.workflows.presentation.me.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record OwnAccountResponse(
        UUID id, String email, String pendingEmail, String firstName, String lastName, LocalDate birthDate,
        Set<String> roles, String status, Instant emailVerifiedAt, Instant createdAt, Instant updatedAt
) {

    public OwnAccountResponse {
        roles = Set.copyOf(roles);
    }
}

