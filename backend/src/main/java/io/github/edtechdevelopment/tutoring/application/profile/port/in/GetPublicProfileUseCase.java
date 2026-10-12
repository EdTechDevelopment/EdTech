package io.github.edtechdevelopment.tutoring.application.profile.port.in;

import io.github.edtechdevelopment.tutoring.api.model.profile.StudentProfileView;
import io.github.edtechdevelopment.tutoring.api.model.profile.TeacherProfileView;

import java.util.Optional;
import java.util.UUID;

public interface GetPublicProfileUseCase {

    Optional<TeacherProfileView> findPublicTeacherProfile(UUID userId);

    Optional<StudentProfileView> findPublicStudentProfile(UUID userId);
}
