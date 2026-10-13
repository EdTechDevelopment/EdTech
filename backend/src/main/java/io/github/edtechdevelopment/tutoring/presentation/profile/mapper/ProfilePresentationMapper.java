package io.github.edtechdevelopment.tutoring.presentation.profile.mapper;

import io.github.edtechdevelopment.tutoring.api.model.profile.StudentProfileView;
import io.github.edtechdevelopment.tutoring.api.model.profile.TeacherProfileView;
import io.github.edtechdevelopment.tutoring.api.model.profile.search.StudentProfileCardView;
import io.github.edtechdevelopment.tutoring.api.model.profile.search.TeacherProfileCardView;
import io.github.edtechdevelopment.tutoring.application.profile.model.command.UpdateStudentProfileCommand;
import io.github.edtechdevelopment.tutoring.application.profile.model.command.UpdateTeacherProfileCommand;
import io.github.edtechdevelopment.tutoring.presentation.profile.model.StudentProfileCardResponse;
import io.github.edtechdevelopment.tutoring.presentation.profile.model.StudentProfileResponse;
import io.github.edtechdevelopment.tutoring.presentation.profile.model.TeacherProfileCardResponse;
import io.github.edtechdevelopment.tutoring.presentation.profile.model.TeacherProfileResponse;
import io.github.edtechdevelopment.tutoring.presentation.profile.model.UpdateStudentProfileRequest;
import io.github.edtechdevelopment.tutoring.presentation.profile.model.UpdateTeacherProfileRequest;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ProfilePresentationMapper {
    public UpdateTeacherProfileCommand toCommand(UUID id, UpdateTeacherProfileRequest request) {
        return new UpdateTeacherProfileCommand(id, request.displayName(), request.contactEmail(), request.contactDetails(),
                request.subjectCodes(), request.description(), request.education(), request.experienceYears(), request.city(), request.photoUrl());
    }
    public UpdateStudentProfileCommand toCommand(UUID id, UpdateStudentProfileRequest request) {
        return new UpdateStudentProfileCommand(id, request.displayName(), request.contactEmail(), request.contactDetails(), request.subjectCodes(), request.photoUrl());
    }
    public TeacherProfileResponse toResponse(TeacherProfileView view) {
        return new TeacherProfileResponse(view.userId(), view.age(), view.displayName(), view.contactEmail(), view.contactDetails(),
                view.subjectCodes(), view.description(), view.education(), view.experienceYears(), view.city(), view.photoUrl());
    }
    public StudentProfileResponse toResponse(StudentProfileView view) {
        return new StudentProfileResponse(view.userId(), view.age(), view.displayName(), view.contactEmail(), view.contactDetails(), view.subjectCodes(), view.photoUrl());
    }
    public TeacherProfileCardResponse toResponse(TeacherProfileCardView view) {
        return new TeacherProfileCardResponse(view.userId(), view.displayName(), view.age(), view.photoUrl(), view.subjectCodes(), view.city(), view.descriptionPreview());
    }
    public StudentProfileCardResponse toResponse(StudentProfileCardView view) {
        return new StudentProfileCardResponse(view.userId(), view.displayName(), view.age(), view.photoUrl(), view.subjectCodes());
    }
}
