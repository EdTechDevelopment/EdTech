package io.github.edtechdevelopment.tutoring.infrastructure.profile.persistence.student;

import io.github.edtechdevelopment.tutoring.domain.profile.model.ProfileEmail;
import io.github.edtechdevelopment.tutoring.domain.profile.model.StudentProfile;
import io.github.edtechdevelopment.tutoring.domain.subject.model.SubjectCode;
import io.github.edtechdevelopment.tutoring.infrastructure.persistence.InvalidPersistenceDataException;
import io.github.edtechdevelopment.tutoring.infrastructure.persistence.data.generated.tables.records.TutoringStudentProfilesRecord;

import org.jooq.JSONB;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class StudentProfilePersistenceMapper {
    private final ObjectMapper json;
    public StudentProfilePersistenceMapper(ObjectMapper json) {
        this.json = json;
    }

    public StudentProfile toDomain(StudentProfilePersistenceData data) {
        var row = data.profile();
        try {
            Set<SubjectCode> subjects = new HashSet<>();
            for (String code : data.subjectCodes()) {
                subjects.add(new SubjectCode(code));
            }
            List<String> contacts = List.of(json.readValue(row.getContactDetails().data(), String[].class));
            return StudentProfile.reconstitute(row.getUserId(), row.getDisplayName(),
                    new ProfileEmail(row.getContactEmail()), contacts, subjects,
                    row.getPhotoUrl() == null ? null : URI.create(row.getPhotoUrl()), row.getCreatedAt().toInstant());
        } catch (RuntimeException exception) {
            throw new InvalidPersistenceDataException("Student profile contains invalid stored data", exception);
        }
    }

    public StudentProfilePersistenceData toPersistence(StudentProfile profile) {
        var row = new TutoringStudentProfilesRecord();
        row.setUserId(profile.userId());
        row.setCreatedAt(profile.createdAt().atOffset(ZoneOffset.UTC));
        row.setDisplayName(profile.displayName());
        row.setContactEmail(profile.contactEmail().value());
        row.setContactDetails(JSONB.valueOf(json.writeValueAsString(profile.contactDetails())));
        row.setPhotoUrl(profile.photoUrl() == null ? null : profile.photoUrl().toString());
        
        Set<String> subjects = new HashSet<>();
        for (SubjectCode code : profile.subjectCodes()) {
            subjects.add(code.value());
        }
        return new StudentProfilePersistenceData(row, subjects);
    }
}
