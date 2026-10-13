package io.github.edtechdevelopment.tutoring.application.profile.service;

import io.github.edtechdevelopment.tutoring.api.exception.ProfileNotFoundException;
import io.github.edtechdevelopment.tutoring.api.model.profile.ProfileTypeView;
import io.github.edtechdevelopment.tutoring.api.query.TutoringSubjectQuery;
import io.github.edtechdevelopment.tutoring.application.profile.mapper.ProfileInputMapper;
import io.github.edtechdevelopment.tutoring.application.profile.model.command.UpdateStudentProfileCommand;
import io.github.edtechdevelopment.tutoring.application.profile.port.in.UpdateStudentProfileUseCase;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.StudentProfileRepository;
import io.github.edtechdevelopment.tutoring.domain.profile.model.StudentProfile;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

public class StudentProfileUpdateService implements UpdateStudentProfileUseCase {

    private final StudentProfileRepository studentProfileRepository;
    private final TutoringSubjectQuery subjectQuery;
    private final ProfileInputMapper profileInputMapper;

    public StudentProfileUpdateService(
            StudentProfileRepository studentProfileRepository,
            TutoringSubjectQuery subjectQuery,
            ProfileInputMapper profileInputMapper) {

        this.studentProfileRepository = Objects.requireNonNull(studentProfileRepository, "Student profile repository must not be null");
        this.subjectQuery = Objects.requireNonNull(subjectQuery, "Subject query must not be null");
        this.profileInputMapper = Objects.requireNonNull(profileInputMapper, "Profile input mapper must not be null");
    }

    @Override
    @Transactional
    public void execute(UpdateStudentProfileCommand command) {
        Objects.requireNonNull(command, "Update student profile command must not be null");
        if (!command.subjectCodes().isEmpty()) {
            subjectQuery.requireAllExist(command.subjectCodes());
        }

        StudentProfile profile = studentProfileRepository.findByUserIdForUpdate(command.actorUserId())
                .orElseThrow(() -> new ProfileNotFoundException(ProfileTypeView.STUDENT, command.actorUserId()));
        StudentProfile requested = profileInputMapper.toStudentProfile(command, profile.createdAt());
        profile.updateDetails(requested.displayName(), requested.contactDetails(), requested.photoUrl());
        profile.changeSubjects(requested.subjectCodes());
        profile.changeContactEmail(requested.contactEmail());
        studentProfileRepository.update(profile);
    }
}
