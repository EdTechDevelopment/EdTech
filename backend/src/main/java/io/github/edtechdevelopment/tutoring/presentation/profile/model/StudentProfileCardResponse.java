package io.github.edtechdevelopment.tutoring.presentation.profile.model;

import java.util.Set;
import java.util.UUID;

public record StudentProfileCardResponse(UUID userId, String displayName, int age, String photoUrl, Set<String> subjectCodes) {
    public StudentProfileCardResponse {
        subjectCodes = Set.copyOf(subjectCodes);
    }
}
