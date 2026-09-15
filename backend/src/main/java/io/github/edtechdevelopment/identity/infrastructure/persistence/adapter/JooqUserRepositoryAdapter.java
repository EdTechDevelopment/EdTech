package io.github.edtechdevelopment.identity.infrastructure.persistence.adapter;

import io.github.edtechdevelopment.identity.application.exception.EmailAlreadyExistsException;
import io.github.edtechdevelopment.identity.application.port.out.persistence.UserRepository;
import io.github.edtechdevelopment.identity.domain.user.model.Email;
import io.github.edtechdevelopment.identity.domain.user.model.User;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.model.UserPersistenceData;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.repository.UserJooqRepository;
import io.github.edtechdevelopment.identity.infrastructure.persistence.mapper.UserPersistenceMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JooqUserRepositoryAdapter implements UserRepository {

    private final UserJooqRepository repository;
    private final UserPersistenceMapper mapper;

    public JooqUserRepositoryAdapter(UserJooqRepository repository, UserPersistenceMapper mapper) {
        this.repository = Objects.requireNonNull(repository, "User jOOQ repository must not be null");
        this.mapper = Objects.requireNonNull(mapper, "User persistence mapper must not be null");
    }

    @Override
    public Optional<User> findById(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return repository.findById(userId).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByIdForUpdate(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return repository.findByIdForUpdate(userId).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        Objects.requireNonNull(email, "Email must not be null");
        return repository.findByCurrentEmail(email.value()).map(mapper::toDomain);
    }

    @Override
    public boolean existsByEmail(Email email) {
        Objects.requireNonNull(email, "Email must not be null");
        return repository.existsByEmail(email.value());
    }

    @Override
    public void save(User user) {
        Objects.requireNonNull(user, "User must not be null");
        UserPersistenceData data = mapper.toPersistence(user);

        try {
            repository.save(data);
        } catch (DuplicateKeyException exception) {
            throw new EmailAlreadyExistsException(exception);
        }
    }
}
