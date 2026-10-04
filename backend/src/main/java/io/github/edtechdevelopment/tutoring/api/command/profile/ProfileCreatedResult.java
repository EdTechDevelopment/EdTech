package io.github.edtechdevelopment.tutoring.api.command.profile;

import io.github.edtechdevelopment.tutoring.api.model.profile.ProfileTypeView;

import java.util.Objects;
import java.util.UUID;

public record ProfileCreatedResult(UUID userId, ProfileTypeView profileType) {

    public ProfileCreatedResult {
        Objects.requireNonNull(userId, "User id must not be null");
        Objects.requireNonNull(profileType, "Profile type must not be null");
    }
}
