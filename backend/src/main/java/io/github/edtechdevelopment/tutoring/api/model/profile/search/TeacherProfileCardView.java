package io.github.edtechdevelopment.tutoring.api.model.profile.search;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record TeacherProfileCardView(
        UUID userId,
        String displayName,
        int age,
        String photoUrl,
        Set<String> subjectCodes,
        String city,
        String descriptionPreview
) {

    public TeacherProfileCardView {
        Objects.requireNonNull(userId, "User id must not be null");
        Objects.requireNonNull(displayName, "Display name must not be null");
        if (age < 0) {
            throw new IllegalArgumentException("Age must not be negative");
        }
        subjectCodes = Set.copyOf(Objects.requireNonNull(subjectCodes, "Subject codes must not be null"));
    }
}
