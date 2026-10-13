package io.github.edtechdevelopment.identity.api.query;

import io.github.edtechdevelopment.identity.api.model.OwnAccountView;

import java.util.Optional;
import java.util.UUID;

public interface OwnAccountQuery {

    Optional<OwnAccountView> findAccountForOwner(UUID userId);
}

