package io.github.edtechdevelopment.identity.api.query;

import io.github.edtechdevelopment.identity.api.model.UserSummary;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface IdentityQuery {

    Optional<UserSummary> findUserById(UUID userId);

    Map<UUID, UserSummary> findUsersByIds(Set<UUID> userIds);

    Optional<UserSummary> findUserByVerifiedEmail(String normalizedEmail);

    Set<UUID> findActiveUserIds(Set<UUID> userIds);

    Map<UUID, Integer> findAgesByIds(Set<UUID> userIds, LocalDate asOf);
}
