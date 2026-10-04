package io.github.edtechdevelopment.tutoring.api.model.profile.input;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record TeacherProfileData(
        String displayName,
        String contactEmail,
        List<String> contactDetails,
        Set<String> subjectCodes,
        String description,
        String education,
        Integer experienceYears,
        String city,
        String photoUrl
) {

    public TeacherProfileData {
        Objects.requireNonNull(displayName, "Display name must not be null");
        Objects.requireNonNull(contactEmail, "Contact email must not be null");
        contactDetails = List.copyOf(Objects.requireNonNull(contactDetails, "Contact details must not be null"));
        subjectCodes = Set.copyOf(Objects.requireNonNull(subjectCodes, "Subject codes must not be null"));
    }
}
