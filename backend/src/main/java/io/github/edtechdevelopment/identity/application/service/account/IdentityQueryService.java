package io.github.edtechdevelopment.identity.application.service.account;

import io.github.edtechdevelopment.identity.api.model.UserSummary;
import io.github.edtechdevelopment.identity.api.query.IdentityQuery;
import io.github.edtechdevelopment.identity.application.mapper.IdentityApiMapper;
import io.github.edtechdevelopment.identity.application.port.out.persistence.UserRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class IdentityQueryService implements IdentityQuery {

    private final UserRepository userRepository;
    private final IdentityApiMapper identityApiMapper;

    public IdentityQueryService(
            UserRepository userRepository,
            IdentityApiMapper identityApiMapper
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "User repository must not be null");
        this.identityApiMapper = Objects.requireNonNull(
                identityApiMapper,
                "Identity API mapper must not be null"
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserSummary> findUserById(UUID userId) {
        Objects.requireNonNull(userId, "User id must not be null");
        return userRepository.findById(userId).map(identityApiMapper::toUserSummary);
    }
}
