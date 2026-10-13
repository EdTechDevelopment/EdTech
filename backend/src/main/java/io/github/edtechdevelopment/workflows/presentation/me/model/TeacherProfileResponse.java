package io.github.edtechdevelopment.workflows.presentation.me.model;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record TeacherProfileResponse(
        UUID userId, int age, String displayName, String contactEmail, List<String> contactDetails,
        Set<String> subjectCodes, String description, String education, Integer experienceYears, String city, String photoUrl
) {

    public TeacherProfileResponse {
        contactDetails = List.copyOf(contactDetails);
        subjectCodes = Set.copyOf(subjectCodes);
    }
}

