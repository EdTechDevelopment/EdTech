package io.github.edtechdevelopment.identity.application.service.account;

import io.github.edtechdevelopment.identity.api.model.OwnAccountView;
import io.github.edtechdevelopment.identity.api.model.UserRoleView;
import io.github.edtechdevelopment.identity.api.model.UserStatusView;
import io.github.edtechdevelopment.identity.api.query.OwnAccountQuery;
import io.github.edtechdevelopment.identity.application.port.out.persistence.UserRepository;

import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class OwnAccountQueryService implements OwnAccountQuery {

    private final UserRepository userRepository;

    public OwnAccountQueryService(UserRepository userRepository) {
        this.userRepository = Objects.requireNonNull(userRepository, "User repository must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OwnAccountView> findAccountForOwner(UUID userId) {
        Objects.requireNonNull(userId, "User id must not be null");
        return userRepository.findById(userId).map(user -> new OwnAccountView(
                user.id(), user.email().value(), user.pendingEmail().map(email -> email.value()).orElse(null),
                user.firstName(), user.lastName(), user.birthDate(),
                user.roles().stream().map(role -> UserRoleView.valueOf(role.name())).collect(Collectors.toUnmodifiableSet()),
                UserStatusView.valueOf(user.status().name()), user.emailVerifiedAt().orElse(null),
                user.createdAt(), user.updatedAt()));
    }
}

