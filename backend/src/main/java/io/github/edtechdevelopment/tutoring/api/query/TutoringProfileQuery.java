package io.github.edtechdevelopment.tutoring.api.query;

import io.github.edtechdevelopment.tutoring.api.model.profile.StudentProfileView;
import io.github.edtechdevelopment.tutoring.api.model.profile.TeacherProfileView;
import io.github.edtechdevelopment.tutoring.api.model.profile.summary.StudentProfileSummary;
import io.github.edtechdevelopment.tutoring.api.model.profile.summary.TeacherProfileSummary;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface TutoringProfileQuery {

    Optional<TeacherProfileView> findTeacherProfile(UUID userId);

    Optional<StudentProfileView> findStudentProfile(UUID userId);

    Optional<TeacherProfileView> findPublicTeacherProfile(UUID userId);

    Optional<StudentProfileView> findPublicStudentProfile(UUID userId);

    void requireTeacherProfile(UUID teacherUserId);

    Map<UUID, TeacherProfileSummary> findTeacherSummaries(Set<UUID> teacherUserIds);

    Map<UUID, StudentProfileSummary> findStudentSummaries(Set<UUID> studentUserIds);

    Optional<StudentProfileView> findLinkedStudentProfile(UUID teacherUserId, UUID studentUserId);

    Optional<TeacherProfileView> findLinkedTeacherProfile(UUID studentUserId, UUID teacherUserId);
}
