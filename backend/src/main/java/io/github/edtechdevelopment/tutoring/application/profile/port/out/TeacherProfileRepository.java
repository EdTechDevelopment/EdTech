package io.github.edtechdevelopment.tutoring.application.profile.port.out;

import io.github.edtechdevelopment.tutoring.application.profile.model.query.ProfileNameData;
import io.github.edtechdevelopment.tutoring.application.profile.model.query.PublicProfilePageCursor;
import io.github.edtechdevelopment.tutoring.application.profile.model.query.PublicProfileSearchCandidate;
import io.github.edtechdevelopment.tutoring.domain.profile.model.TeacherProfile;
import io.github.edtechdevelopment.tutoring.domain.subject.model.SubjectCode;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface TeacherProfileRepository {

    boolean existsByUserId(UUID userId);

    Optional<TeacherProfile> findByUserId(UUID userId);

    Optional<TeacherProfile> findByUserIdForUpdate(UUID userId);

    Map<UUID, TeacherProfile> findByUserIds(Set<UUID> userIds);

    Map<UUID, ProfileNameData> findSummariesByUserIds(Set<UUID> userIds);

    List<PublicProfileSearchCandidate> findSearchCandidates(
            SubjectCode subjectCode, PublicProfilePageCursor after, int fetchLimit);

    void insert(TeacherProfile profile);

    void update(TeacherProfile profile);
}
