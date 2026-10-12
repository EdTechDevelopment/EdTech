package io.github.edtechdevelopment.tutoring.application.profile.service;

import io.github.edtechdevelopment.tutoring.api.command.profile.CreateInitialProfilesCommand;
import io.github.edtechdevelopment.tutoring.api.command.profile.CreateStudentProfileCommand;
import io.github.edtechdevelopment.tutoring.api.command.profile.CreateTeacherProfileCommand;
import io.github.edtechdevelopment.tutoring.api.command.profile.InitialProfilesCreatedResult;
import io.github.edtechdevelopment.tutoring.api.command.profile.ProfileCreatedResult;
import io.github.edtechdevelopment.tutoring.api.command.profile.TutoringProfileCreationCommands;
import io.github.edtechdevelopment.tutoring.api.exception.InvalidProfileDataException;
import io.github.edtechdevelopment.tutoring.api.exception.ProfileAlreadyExistsException;
import io.github.edtechdevelopment.tutoring.api.model.profile.ProfileTypeView;
import io.github.edtechdevelopment.tutoring.api.model.profile.input.StudentProfileData;
import io.github.edtechdevelopment.tutoring.api.model.profile.input.TeacherProfileData;
import io.github.edtechdevelopment.tutoring.api.query.TutoringSubjectQuery;
import io.github.edtechdevelopment.tutoring.application.integration.identity.port.out.IdentityAccountGateway;
import io.github.edtechdevelopment.tutoring.application.profile.mapper.ProfileInputMapper;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.StudentProfileRepository;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.TeacherProfileRepository;
import io.github.edtechdevelopment.tutoring.domain.profile.model.ProfileType;
import io.github.edtechdevelopment.tutoring.domain.profile.model.StudentProfile;
import io.github.edtechdevelopment.tutoring.domain.profile.model.TeacherProfile;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class RegistrationProfileService implements TutoringProfileCreationCommands {

    private final TeacherProfileRepository teacherProfileRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final IdentityAccountGateway identityAccountGateway;
    private final TutoringSubjectQuery subjectQuery;
    private final ProfileInputMapper profileInputMapper;
    private final Clock clock;

    public RegistrationProfileService(
            TeacherProfileRepository teacherProfileRepository,
            StudentProfileRepository studentProfileRepository,
            IdentityAccountGateway identityAccountGateway,
            TutoringSubjectQuery subjectQuery,
            ProfileInputMapper profileInputMapper,
            Clock clock) {

        this.teacherProfileRepository = Objects.requireNonNull(teacherProfileRepository, "Teacher profile repository must not be null");
        this.studentProfileRepository = Objects.requireNonNull(studentProfileRepository, "Student profile repository must not be null");
        this.identityAccountGateway = Objects.requireNonNull(identityAccountGateway, "Identity account gateway must not be null");
        this.subjectQuery = Objects.requireNonNull(subjectQuery, "Subject query must not be null");
        this.profileInputMapper = Objects.requireNonNull(profileInputMapper, "Profile input mapper must not be null");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public InitialProfilesCreatedResult createInitialProfiles(CreateInitialProfilesCommand command) {
        Objects.requireNonNull(command, "Create initial profiles command must not be null");
        if (command.teacherProfile().isEmpty() && command.studentProfile().isEmpty()) {
            throw new InvalidProfileDataException(null, Set.of("teacherProfile", "studentProfile"));
        }

        TeacherProfile teacher = null;
        StudentProfile student = null;
        Instant createdAt = clock.instant();
        Set<String> subjectCodes = new HashSet<>();
        if (command.teacherProfile().isPresent()) {
            TeacherProfileData data = command.teacherProfile().orElseThrow();
            teacher = prepareTeacherProfile(command.userId(), data, createdAt);
            subjectCodes.addAll(data.subjectCodes());
        }
        if (command.studentProfile().isPresent()) {
            StudentProfileData data = command.studentProfile().orElseThrow();
            student = prepareStudentProfile(command.userId(), data, createdAt);
            subjectCodes.addAll(data.subjectCodes());
        }
        requireSubjectsExist(subjectCodes);

        Set<ProfileTypeView> createdProfiles = EnumSet.noneOf(ProfileTypeView.class);
        if (teacher != null) {
            teacherProfileRepository.insert(teacher);
            createdProfiles.add(ProfileTypeView.TEACHER);
        }
        if (student != null) {
            studentProfileRepository.insert(student);
            createdProfiles.add(ProfileTypeView.STUDENT);
        }
        return new InitialProfilesCreatedResult(command.userId(), createdProfiles);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ProfileCreatedResult createTeacherProfile(CreateTeacherProfileCommand command) {
        Objects.requireNonNull(command, "Create teacher profile command must not be null");
        TeacherProfile profile = prepareTeacherProfile(command.userId(), command.profile(), clock.instant());
        requireSubjectsExist(command.profile().subjectCodes());
        teacherProfileRepository.insert(profile);
        return new ProfileCreatedResult(command.userId(), ProfileTypeView.TEACHER);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ProfileCreatedResult createStudentProfile(CreateStudentProfileCommand command) {
        Objects.requireNonNull(command, "Create student profile command must not be null");
        StudentProfile profile = prepareStudentProfile(command.userId(), command.profile(), clock.instant());
        requireSubjectsExist(command.profile().subjectCodes());
        studentProfileRepository.insert(profile);
        return new ProfileCreatedResult(command.userId(), ProfileTypeView.STUDENT);
    }

    private TeacherProfile prepareTeacherProfile(UUID userId, TeacherProfileData data, Instant createdAt) {
        identityAccountGateway.requireRole(userId, ProfileType.TEACHER);
        if (teacherProfileRepository.existsByUserId(userId)) {
            throw new ProfileAlreadyExistsException(ProfileTypeView.TEACHER, userId);
        }
        return profileInputMapper.toTeacherProfile(userId, data, createdAt);
    }

    private StudentProfile prepareStudentProfile(UUID userId, StudentProfileData data, Instant createdAt) {
        identityAccountGateway.requireRole(userId, ProfileType.STUDENT);
        if (studentProfileRepository.existsByUserId(userId)) {
            throw new ProfileAlreadyExistsException(ProfileTypeView.STUDENT, userId);
        }
        return profileInputMapper.toStudentProfile(userId, data, createdAt);
    }

    private void requireSubjectsExist(Set<String> subjectCodes) {
        if (!subjectCodes.isEmpty()) {
            subjectQuery.requireAllExist(Set.copyOf(subjectCodes));
        }
    }
}
