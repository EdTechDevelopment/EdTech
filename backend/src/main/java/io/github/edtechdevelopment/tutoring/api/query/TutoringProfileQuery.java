package io.github.edtechdevelopment.tutoring.api.query;

import io.github.edtechdevelopment.tutoring.api.model.profile.publicview.PublicStudentProfileView;
import io.github.edtechdevelopment.tutoring.api.model.profile.publicview.PublicTeacherProfileView;
import io.github.edtechdevelopment.tutoring.api.model.profile.self.StudentProfileView;
import io.github.edtechdevelopment.tutoring.api.model.profile.self.TeacherProfileView;
import io.github.edtechdevelopment.tutoring.api.model.profile.summary.StudentProfileSummary;
import io.github.edtechdevelopment.tutoring.api.model.profile.summary.TeacherProfileSummary;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface TutoringProfileQuery {

    Optional<TeacherProfileView> findTeacherProfile(UUID userId);

    Optional<StudentProfileView> findStudentProfile(UUID userId);

    Optional<PublicTeacherProfileView> findPublicTeacherProfile(UUID userId);

    Optional<PublicStudentProfileView> findPublicStudentProfile(UUID userId);

    void requireTeacherProfile(UUID teacherUserId);

    Map<UUID, TeacherProfileSummary> findTeacherSummaries(Set<UUID> teacherUserIds);

    Map<UUID, StudentProfileSummary> findStudentSummaries(Set<UUID> studentUserIds);

    Optional<PublicStudentProfileView> findLinkedStudentProfile(UUID teacherUserId, UUID studentUserId);

    Optional<PublicTeacherProfileView> findLinkedTeacherProfile(UUID studentUserId, UUID teacherUserId);
}
