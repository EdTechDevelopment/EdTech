package io.github.edtechdevelopment.tutoring.application.profile.model.query;

import java.util.Objects;
import java.util.UUID;

public record ProfileNameData(UUID userId, String displayName) {

    public ProfileNameData {
        Objects.requireNonNull(userId, "User id must not be null");
        Objects.requireNonNull(displayName, "Display name must not be null");
    }
}
