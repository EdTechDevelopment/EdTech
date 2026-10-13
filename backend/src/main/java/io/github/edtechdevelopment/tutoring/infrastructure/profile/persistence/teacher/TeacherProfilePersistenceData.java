package io.github.edtechdevelopment.tutoring.infrastructure.profile.persistence.teacher;

import io.github.edtechdevelopment.tutoring.infrastructure.persistence.data.generated.tables.records.TutoringTeacherProfilesRecord;

import java.util.Set;

public record TeacherProfilePersistenceData(TutoringTeacherProfilesRecord profile, Set<String> subjectCodes) {
    public TeacherProfilePersistenceData {
        subjectCodes = Set.copyOf(subjectCodes);
    }
}
