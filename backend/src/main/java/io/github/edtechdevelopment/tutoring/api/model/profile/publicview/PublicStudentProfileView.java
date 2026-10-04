package io.github.edtechdevelopment.tutoring.api.model.profile.publicview;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record PublicStudentProfileView(
        UUID userId,
        int age,
        String displayName,
        String contactEmail,
        List<String> contactDetails,
        Set<String> subjectCodes,
        String photoUrl
) {

    public PublicStudentProfileView {
        Objects.requireNonNull(userId, "User id must not be null");
        if (age < 0) {
            throw new IllegalArgumentException("Age must not be negative");
        }
        Objects.requireNonNull(displayName, "Display name must not be null");
        Objects.requireNonNull(contactEmail, "Contact email must not be null");
        contactDetails = List.copyOf(Objects.requireNonNull(contactDetails, "Contact details must not be null"));
        subjectCodes = Set.copyOf(Objects.requireNonNull(subjectCodes, "Subject codes must not be null"));
    }
}
