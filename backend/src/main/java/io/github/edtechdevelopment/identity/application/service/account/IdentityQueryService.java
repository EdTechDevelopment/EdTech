package io.github.edtechdevelopment.identity.application.service.account;

import io.github.edtechdevelopment.identity.api.model.UserSummary;
import io.github.edtechdevelopment.identity.api.query.IdentityQuery;
import io.github.edtechdevelopment.identity.application.mapper.IdentityApiMapper;
import io.github.edtechdevelopment.identity.application.port.out.persistence.UserRepository;
import io.github.edtechdevelopment.identity.domain.user.model.Email;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, UserSummary> findUsersByIds(Set<UUID> userIds) {
        Objects.requireNonNull(userIds, "User ids must not be null");
        return userRepository.findByIds(userIds).entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> identityApiMapper.toUserSummary(entry.getValue())
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserSummary> findUserByVerifiedEmail(String normalizedEmail) {
        Objects.requireNonNull(normalizedEmail, "Email must not be null");
        Email email = new Email(normalizedEmail);
        if (!email.value().equals(normalizedEmail)) {
            throw new IllegalArgumentException("Email must be normalized");
        }
        return userRepository.findByVerifiedEmail(email).map(identityApiMapper::toUserSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> findActiveUserIds(Set<UUID> userIds) {
        Objects.requireNonNull(userIds, "User ids must not be null");
        return userRepository.findActiveUserIds(userIds);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Integer> findAgesByIds(Set<UUID> userIds, LocalDate asOf) {
        Objects.requireNonNull(userIds, "User ids must not be null");
        Objects.requireNonNull(asOf, "Age calculation date must not be null");
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findByIds(userIds).entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> Period.between(entry.getValue().birthDate(), asOf).getYears()
                ));
    }
}
