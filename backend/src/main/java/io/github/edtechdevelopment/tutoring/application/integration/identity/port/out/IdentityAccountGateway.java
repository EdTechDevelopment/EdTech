package io.github.edtechdevelopment.tutoring.application.integration.identity.port.out;

import io.github.edtechdevelopment.tutoring.domain.profile.model.ProfileType;
import io.github.edtechdevelopment.tutoring.application.integration.identity.model.VerifiedAccountReference;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface IdentityAccountGateway {

    void requireRole(UUID userId, ProfileType profileType);

    Map<UUID, Integer> findAgesByIds(Set<UUID> userIds, LocalDate asOf);

    Set<UUID> findActiveUserIds(Set<UUID> userIds);

    Optional<VerifiedAccountReference> findVerifiedAccountById(UUID userId);

    Optional<VerifiedAccountReference> findByVerifiedEmail(String normalizedEmail);
}
