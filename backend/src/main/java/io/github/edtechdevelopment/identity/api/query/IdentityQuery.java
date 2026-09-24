package io.github.edtechdevelopment.identity.api.query;

import io.github.edtechdevelopment.identity.api.model.UserSummary;

import java.util.Optional;
import java.util.UUID;

public interface IdentityQuery {

    Optional<UserSummary> findUserById(UUID userId);
}
