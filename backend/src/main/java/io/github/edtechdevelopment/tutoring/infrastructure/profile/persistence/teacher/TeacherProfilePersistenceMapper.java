package io.github.edtechdevelopment.tutoring.infrastructure.profile.persistence.teacher;

import io.github.edtechdevelopment.tutoring.domain.profile.model.ProfileEmail;
import io.github.edtechdevelopment.tutoring.domain.profile.model.TeacherProfile;
import io.github.edtechdevelopment.tutoring.domain.subject.model.SubjectCode;
import io.github.edtechdevelopment.tutoring.infrastructure.persistence.InvalidPersistenceDataException;
import io.github.edtechdevelopment.tutoring.infrastructure.persistence.data.generated.tables.records.TutoringTeacherProfilesRecord;

import org.jooq.JSONB;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class TeacherProfilePersistenceMapper {
    private final ObjectMapper json;
    public TeacherProfilePersistenceMapper(ObjectMapper json) {
        this.json = json;
    }

    public TeacherProfile toDomain(TeacherProfilePersistenceData data) {
        var row = data.profile();
        try {
            Set<SubjectCode> subjects = new HashSet<>();
            for (String code : data.subjectCodes()) {
                subjects.add(new SubjectCode(code));
            }
            List<String> contacts = List.of(json.readValue(row.getContactDetails().data(), String[].class));
            return TeacherProfile.reconstitute(row.getUserId(), row.getDisplayName(),
                    new ProfileEmail(row.getContactEmail()), contacts, subjects,
                    row.getDescription(), row.getEducation(), row.getExperienceYears(), row.getCity(),
                    row.getPhotoUrl() == null ? null : URI.create(row.getPhotoUrl()), row.getCreatedAt().toInstant());
        } catch (RuntimeException exception) {
            throw new InvalidPersistenceDataException("Teacher profile contains invalid stored data", exception);
        }
    }

    public TeacherProfilePersistenceData toPersistence(TeacherProfile profile) {
        var row = new TutoringTeacherProfilesRecord();
        row.setUserId(profile.userId());
        row.setCreatedAt(profile.createdAt().atOffset(ZoneOffset.UTC));
        row.setDisplayName(profile.displayName());
        row.setContactEmail(profile.contactEmail().value());
        row.setContactDetails(JSONB.valueOf(json.writeValueAsString(profile.contactDetails())));
        row.setPhotoUrl(profile.photoUrl() == null ? null : profile.photoUrl().toString());
        row.setDescription(profile.description());
        row.setEducation(profile.education());
        row.setExperienceYears(profile.experienceYears());
        row.setCity(profile.city());
        Set<String> subjects = new HashSet<>();
        for (SubjectCode code : profile.subjectCodes()) {
            subjects.add(code.value());
        }
        return new TeacherProfilePersistenceData(row, subjects);
    }
}
