package io.github.edtechdevelopment.tutoring.infrastructure.integration.identity;

import io.github.edtechdevelopment.identity.api.model.UserRoleView;
import io.github.edtechdevelopment.identity.api.query.IdentityQuery;
import io.github.edtechdevelopment.tutoring.application.integration.identity.exception.IdentityUserConsistencyException;
import io.github.edtechdevelopment.tutoring.application.integration.identity.model.VerifiedAccountReference;
import io.github.edtechdevelopment.tutoring.application.integration.identity.port.out.IdentityAccountGateway;
import io.github.edtechdevelopment.tutoring.domain.profile.model.ProfileType;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class IdentityAccountGatewayAdapter implements IdentityAccountGateway {
    private final IdentityQuery identity;
    public IdentityAccountGatewayAdapter(IdentityQuery identity) {
        this.identity = identity;
    }

    @Override
    public void requireRole(UUID userId, ProfileType type) {
        var account = identity.findUserById(userId);
        if (account.isEmpty() || !account.get().roles().contains(UserRoleView.valueOf(type.name()))) {
            throw new IdentityUserConsistencyException(userId, type);
        }
    }
    @Override
    public Map<UUID, Integer> findAgesByIds(Set<UUID> ids, LocalDate asOf) {
        return identity.findAgesByIds(ids, asOf);
    }
    @Override
    public Set<UUID> findActiveUserIds(Set<UUID> ids) {
        return identity.findActiveUserIds(ids);
    }
    @Override
    public Optional<VerifiedAccountReference> findByVerifiedEmail(String email) {
        return identity.findUserByVerifiedEmail(email).map(user -> new VerifiedAccountReference(user.id(), user.email()));
    }
    @Override
    public Optional<VerifiedAccountReference> findVerifiedAccountById(UUID id) {
        return identity.findUserById(id).flatMap(user -> findByVerifiedEmail(user.email())).filter(user -> user.userId().equals(id));
    }
}
