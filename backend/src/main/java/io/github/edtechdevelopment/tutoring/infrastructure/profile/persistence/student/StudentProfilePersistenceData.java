package io.github.edtechdevelopment.tutoring.infrastructure.profile.persistence.student;

import io.github.edtechdevelopment.tutoring.infrastructure.persistence.data.generated.tables.records.TutoringStudentProfilesRecord;

import java.util.Set;

public record StudentProfilePersistenceData(TutoringStudentProfilesRecord profile, Set<String> subjectCodes) {
    public StudentProfilePersistenceData {
        subjectCodes = Set.copyOf(subjectCodes);
    }
}
