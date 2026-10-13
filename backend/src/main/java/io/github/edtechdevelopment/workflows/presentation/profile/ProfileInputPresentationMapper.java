package io.github.edtechdevelopment.workflows.presentation.profile;

import io.github.edtechdevelopment.tutoring.api.model.profile.input.StudentProfileData;
import io.github.edtechdevelopment.tutoring.api.model.profile.input.TeacherProfileData;

import org.springframework.stereotype.Component;

@Component
public class ProfileInputPresentationMapper {

    public TeacherProfileData toData(TeacherProfileRequest request) {
        return new TeacherProfileData(request.displayName(), request.contactEmail(), request.contactDetails(),
                request.subjectCodes(), request.description(), request.education(), request.experienceYears(),
                request.city(), request.photoUrl());
    }

    public StudentProfileData toData(StudentProfileRequest request) {
        return new StudentProfileData(request.displayName(), request.contactEmail(), request.contactDetails(),
                request.subjectCodes(), request.photoUrl());
    }
}

