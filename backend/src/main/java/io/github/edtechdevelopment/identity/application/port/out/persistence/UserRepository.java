package io.github.edtechdevelopment.identity.application.port.out.persistence;

import io.github.edtechdevelopment.identity.domain.user.model.Email;
import io.github.edtechdevelopment.identity.domain.user.model.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    Optional<User> findById(UUID userId);

    Optional<User> findByIdForUpdate(UUID userId);

    Optional<User> findByEmail(Email email);

    boolean existsByEmail(Email email);

    void save(User user);
}
