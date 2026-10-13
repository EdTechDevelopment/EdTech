package io.github.edtechdevelopment.tutoring.presentation.profile.model;

import java.util.Set;
import java.util.UUID;

public record TeacherProfileCardResponse(UUID userId, String displayName, int age, String photoUrl, Set<String> subjectCodes, String city, String descriptionPreview) {
    public TeacherProfileCardResponse {
        subjectCodes = Set.copyOf(subjectCodes);
    }
}
