package io.github.edtechdevelopment.tutoring.application.profile.mapper;

import io.github.edtechdevelopment.tutoring.api.exception.InvalidProfileDataException;
import io.github.edtechdevelopment.tutoring.api.model.profile.ProfileTypeView;
import io.github.edtechdevelopment.tutoring.api.model.profile.input.StudentProfileData;
import io.github.edtechdevelopment.tutoring.api.model.profile.input.TeacherProfileData;
import io.github.edtechdevelopment.tutoring.application.profile.model.command.UpdateStudentProfileCommand;
import io.github.edtechdevelopment.tutoring.application.profile.model.command.UpdateTeacherProfileCommand;
import io.github.edtechdevelopment.tutoring.domain.profile.exception.EmptyTeacherSubjectsException;
import io.github.edtechdevelopment.tutoring.domain.profile.exception.InvalidExperienceException;
import io.github.edtechdevelopment.tutoring.domain.profile.exception.InvalidProfileEmailException;
import io.github.edtechdevelopment.tutoring.domain.profile.exception.InvalidProfileNameException;
import io.github.edtechdevelopment.tutoring.domain.profile.model.ProfileEmail;
import io.github.edtechdevelopment.tutoring.domain.profile.model.StudentProfile;
import io.github.edtechdevelopment.tutoring.domain.profile.model.TeacherProfile;
import io.github.edtechdevelopment.tutoring.domain.subject.exception.InvalidSubjectCodeException;
import io.github.edtechdevelopment.tutoring.domain.subject.model.SubjectCode;

import java.net.URI;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ProfileInputMapper {

    public TeacherProfile toTeacherProfile(UUID userId, TeacherProfileData data, Instant createdAt) {
        return createTeacherProfile(userId, data.displayName(), data.contactEmail(), data.contactDetails(),
                data.subjectCodes(), data.description(), data.education(), data.experienceYears(),
                data.city(), data.photoUrl(), createdAt);
    }

    public TeacherProfile toTeacherProfile(UpdateTeacherProfileCommand command, Instant createdAt) {
        return createTeacherProfile(command.actorUserId(), command.displayName(), command.contactEmail(),
                command.contactDetails(), command.subjectCodes(), command.description(), command.education(),
                command.experienceYears(), command.city(), command.photoUrl(), createdAt);
    }

    public StudentProfile toStudentProfile(UUID userId, StudentProfileData data, Instant createdAt) {
        return createStudentProfile(userId, data.displayName(), data.contactEmail(), data.contactDetails(),
                data.subjectCodes(), data.photoUrl(), createdAt);
    }

    public StudentProfile toStudentProfile(UpdateStudentProfileCommand command, Instant createdAt) {
        return createStudentProfile(command.actorUserId(), command.displayName(), command.contactEmail(),
                command.contactDetails(), command.subjectCodes(), command.photoUrl(), createdAt);
    }

    private static TeacherProfile createTeacherProfile(
            UUID userId, String displayName, String contactEmail, List<String> contactDetails,
            Set<String> subjectCodes, String description, String education, Integer experienceYears,
            String city, String photoUrl, Instant createdAt) {

        ProfileTypeView type = ProfileTypeView.TEACHER;
        ProfileEmail email = toContactEmail(contactEmail, type);
        Set<SubjectCode> subjects = toSubjectCodes(subjectCodes, type);
        URI photo = toPhotoUri(photoUrl, type);
        try {
            return TeacherProfile.create(userId, displayName, email, contactDetails, subjects,
                    description, education, experienceYears, city, photo, createdAt);
        } catch (InvalidProfileNameException ignored) {
            throw invalidField(type, "displayName");
        } catch (EmptyTeacherSubjectsException ignored) {
            throw invalidField(type, "subjectCodes");
        } catch (InvalidExperienceException ignored) {
            throw invalidField(type, "experienceYears");
        }
    }

    private static StudentProfile createStudentProfile(
            UUID userId, String displayName, String contactEmail, List<String> contactDetails,
            Set<String> subjectCodes, String photoUrl, Instant createdAt) {

        ProfileTypeView type = ProfileTypeView.STUDENT;
        ProfileEmail email = toContactEmail(contactEmail, type);
        Set<SubjectCode> subjects = toSubjectCodes(subjectCodes, type);
        URI photo = toPhotoUri(photoUrl, type);
        try {
            return StudentProfile.create(userId, displayName, email, contactDetails, subjects, photo, createdAt);
        } catch (InvalidProfileNameException ignored) {
            throw invalidField(type, "displayName");
        }
    }

    private static ProfileEmail toContactEmail(String value, ProfileTypeView type) {
        try {
            return new ProfileEmail(value);
        } catch (InvalidProfileEmailException ignored) {
            throw invalidField(type, "contactEmail");
        }
    }

    private static Set<SubjectCode> toSubjectCodes(Set<String> values, ProfileTypeView type) {
        Set<SubjectCode> codes = new HashSet<>();
        try {
            for (String value : values) {
                codes.add(new SubjectCode(value));
            }
        } catch (InvalidSubjectCodeException ignored) {
            throw invalidField(type, "subjectCodes");
        }
        return Set.copyOf(codes);
    }

    private static URI toPhotoUri(String value, ProfileTypeView type) {
        if (value == null) {
            return null;
        }
        try {
            URI uri = URI.create(value);
            if (!uri.isAbsolute() || uri.getHost() == null
                    || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
                throw invalidField(type, "photoUrl");
            }
            return uri;
        } catch (IllegalArgumentException ignored) {
            throw invalidField(type, "photoUrl");
        }
    }

    private static InvalidProfileDataException invalidField(ProfileTypeView type, String field) {
        return new InvalidProfileDataException(type, Set.of(field));
    }
}
