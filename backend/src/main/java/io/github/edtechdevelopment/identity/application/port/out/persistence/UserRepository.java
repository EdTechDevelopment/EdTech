package io.github.edtechdevelopment.identity.application.port.out.persistence;

import io.github.edtechdevelopment.identity.domain.user.model.Email;
import io.github.edtechdevelopment.identity.domain.user.model.User;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface UserRepository {

    Optional<User> findById(UUID userId);

    Map<UUID, User> findByIds(Set<UUID> userIds);

    Optional<User> findByIdForUpdate(UUID userId);

    Optional<User> findByEmail(Email email);

    Optional<User> findByVerifiedEmail(Email email);

    Set<UUID> findActiveUserIds(Set<UUID> userIds);

    Optional<User> findByCurrentOrPendingEmail(Email email);

    boolean existsByEmail(Email email);

    void save(User user);
}
