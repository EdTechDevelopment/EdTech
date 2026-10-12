package io.github.edtechdevelopment.tutoring.application.profile.model.command;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record UpdateStudentProfileCommand(
        UUID actorUserId,
        String displayName,
        String contactEmail,
        List<String> contactDetails,
        Set<String> subjectCodes,
        String photoUrl
) {

    public UpdateStudentProfileCommand {
        Objects.requireNonNull(actorUserId, "Actor user id must not be null");
        Objects.requireNonNull(displayName, "Display name must not be null");
        Objects.requireNonNull(contactEmail, "Contact email must not be null");
        contactDetails = List.copyOf(Objects.requireNonNull(contactDetails, "Contact details must not be null"));
        subjectCodes = Set.copyOf(Objects.requireNonNull(subjectCodes, "Subject codes must not be null"));
    }
}
