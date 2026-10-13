package io.github.edtechdevelopment.workflows.presentation.me.model;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record StudentProfileResponse(
        UUID userId, int age, String displayName, String contactEmail,
        List<String> contactDetails, Set<String> subjectCodes, String photoUrl
) {

    public StudentProfileResponse {
        contactDetails = List.copyOf(contactDetails);
        subjectCodes = Set.copyOf(subjectCodes);
    }
}

