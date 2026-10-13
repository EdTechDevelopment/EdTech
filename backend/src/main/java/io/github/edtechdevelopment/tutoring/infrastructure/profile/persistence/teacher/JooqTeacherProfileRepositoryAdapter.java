package io.github.edtechdevelopment.tutoring.infrastructure.profile.persistence.teacher;

import io.github.edtechdevelopment.tutoring.api.exception.ProfileAlreadyExistsException;
import io.github.edtechdevelopment.tutoring.api.model.profile.ProfileTypeView;
import io.github.edtechdevelopment.tutoring.application.profile.model.query.ProfileNameData;
import io.github.edtechdevelopment.tutoring.application.profile.model.query.PublicProfilePageCursor;
import io.github.edtechdevelopment.tutoring.application.profile.model.query.PublicProfileSearchCandidate;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.TeacherProfileRepository;
import io.github.edtechdevelopment.tutoring.domain.profile.model.TeacherProfile;
import io.github.edtechdevelopment.tutoring.domain.subject.model.SubjectCode;
import io.github.edtechdevelopment.tutoring.infrastructure.persistence.PersistenceChecks;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class JooqTeacherProfileRepositoryAdapter implements TeacherProfileRepository {
    private final TeacherProfileJooqRepository repository;
    private final TeacherProfilePersistenceMapper mapper;
    public JooqTeacherProfileRepositoryAdapter(TeacherProfileJooqRepository repository, TeacherProfilePersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public boolean existsByUserId(UUID id) {
        return repository.exists(id);
    }
    @Override
    public Optional<TeacherProfile> findByUserId(UUID id) {
        return Optional.ofNullable(findByUserIds(Set.of(id)).get(id));
    }
    @Override
    public Optional<TeacherProfile> findByUserIdForUpdate(UUID id) {
        return repository.findForUpdate(id).map(mapper::toDomain);
    }

    @Override
    public Map<UUID, TeacherProfile> findByUserIds(Set<UUID> ids) {
        Map<UUID, TeacherProfile> found = new HashMap<>();
        for (var entry : repository.findByUserIds(ids).entrySet()) {
            found.put(entry.getKey(), mapper.toDomain(entry.getValue()));
        }
        return Map.copyOf(found);
    }

    @Override
    public Map<UUID, ProfileNameData> findSummariesByUserIds(Set<UUID> ids) {
        return repository.findNames(ids);
    }
    @Override
    public List<PublicProfileSearchCandidate> findSearchCandidates(SubjectCode subject, PublicProfilePageCursor after, int limit) {
        return repository.findCandidates(subject, after, limit);
    }
    @Override
    public void insert(TeacherProfile profile) {
        try {
            repository.insert(mapper.toPersistence(profile));
        } catch (DuplicateKeyException exception) {
            if (PersistenceChecks.hasConstraint(exception, "pk_tutoring_teacher_profiles")) {
                throw new ProfileAlreadyExistsException(ProfileTypeView.TEACHER, profile.userId());
            }
            throw exception;
        }
    }
    @Override
    public void update(TeacherProfile profile) {
        repository.update(mapper.toPersistence(profile));
    }
}
