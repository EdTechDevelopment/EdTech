package io.github.edtechdevelopment.tutoring.application.profile.service;

import io.github.edtechdevelopment.tutoring.api.exception.InvalidProfileDataException;
import io.github.edtechdevelopment.tutoring.api.exception.ProfileNotFoundException;
import io.github.edtechdevelopment.tutoring.api.model.profile.ProfileTypeView;
import io.github.edtechdevelopment.tutoring.api.query.TutoringSubjectQuery;
import io.github.edtechdevelopment.tutoring.application.profile.mapper.ProfileInputMapper;
import io.github.edtechdevelopment.tutoring.application.profile.model.command.UpdateTeacherProfileCommand;
import io.github.edtechdevelopment.tutoring.application.profile.port.in.UpdateTeacherProfileUseCase;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.TeacherProfileRepository;
import io.github.edtechdevelopment.tutoring.domain.profile.model.TeacherProfile;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;

public class TeacherProfileUpdateService implements UpdateTeacherProfileUseCase {

    private final TeacherProfileRepository teacherProfileRepository;
    private final TutoringSubjectQuery subjectQuery;
    private final ProfileInputMapper profileInputMapper;

    public TeacherProfileUpdateService(
            TeacherProfileRepository teacherProfileRepository,
            TutoringSubjectQuery subjectQuery,
            ProfileInputMapper profileInputMapper) {

        this.teacherProfileRepository = Objects.requireNonNull(teacherProfileRepository, "Teacher profile repository must not be null");
        this.subjectQuery = Objects.requireNonNull(subjectQuery, "Subject query must not be null");
        this.profileInputMapper = Objects.requireNonNull(profileInputMapper, "Profile input mapper must not be null");
    }

    @Override
    @Transactional
    public void execute(UpdateTeacherProfileCommand command) {
        Objects.requireNonNull(command, "Update teacher profile command must not be null");
        if (command.subjectCodes().isEmpty()) {
            throw new InvalidProfileDataException(ProfileTypeView.TEACHER, Set.of("subjectCodes"));
        }
        subjectQuery.requireAllExist(command.subjectCodes());

        TeacherProfile profile = teacherProfileRepository.findByUserIdForUpdate(command.actorUserId())
                .orElseThrow(() -> new ProfileNotFoundException(ProfileTypeView.TEACHER, command.actorUserId()));
        TeacherProfile requested = profileInputMapper.toTeacherProfile(command, profile.createdAt());
        profile.updateDetails(requested.displayName(), requested.contactDetails(), requested.description(),
                requested.education(), requested.experienceYears(), requested.city(), requested.photoUrl());
        profile.changeSubjects(requested.subjectCodes());
        profile.changeContactEmail(requested.contactEmail());
        teacherProfileRepository.update(profile);
    }
}
