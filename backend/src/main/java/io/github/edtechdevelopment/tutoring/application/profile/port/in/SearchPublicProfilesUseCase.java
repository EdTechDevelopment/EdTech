package io.github.edtechdevelopment.tutoring.application.profile.port.in;

import io.github.edtechdevelopment.tutoring.application.profile.model.query.PublicProfileSearchQuery;
import io.github.edtechdevelopment.tutoring.application.profile.model.result.PublicStudentProfilePageResult;
import io.github.edtechdevelopment.tutoring.application.profile.model.result.PublicTeacherProfilePageResult;

public interface SearchPublicProfilesUseCase {

    PublicTeacherProfilePageResult searchTeachers(PublicProfileSearchQuery query);

    PublicStudentProfilePageResult searchStudents(PublicProfileSearchQuery query);
}
