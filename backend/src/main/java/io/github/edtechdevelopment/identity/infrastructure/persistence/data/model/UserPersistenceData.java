package io.github.edtechdevelopment.identity.infrastructure.persistence.data.model;

import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityUserEmailsRecord;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityUserRolesRecord;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityUsersRecord;

import java.util.List;
import java.util.Objects;

public record UserPersistenceData(
        IdentityUsersRecord user,
        List<IdentityUserEmailsRecord> emails,
        List<IdentityUserRolesRecord> roles
) {

    public UserPersistenceData {
        user = Objects.requireNonNull(user, "User record must not be null");
        emails = List.copyOf(Objects.requireNonNull(emails, "Email records must not be null"));
        roles = List.copyOf(Objects.requireNonNull(roles, "Role records must not be null"));
    }
}
