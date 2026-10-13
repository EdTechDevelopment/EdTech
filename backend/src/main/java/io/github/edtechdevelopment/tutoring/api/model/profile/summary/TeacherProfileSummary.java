package io.github.edtechdevelopment.tutoring.api.model.profile.summary;

import java.util.Objects;
import java.util.UUID;

public record TeacherProfileSummary(UUID userId, String displayName) {

    public TeacherProfileSummary {
        Objects.requireNonNull(userId, "User id must not be null");
        Objects.requireNonNull(displayName, "Display name must not be null");
    }
}
