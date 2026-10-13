package io.github.edtechdevelopment.tutoring.api.command.profile;

import io.github.edtechdevelopment.tutoring.api.model.profile.ProfileTypeView;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record InitialProfilesCreatedResult(UUID userId, Set<ProfileTypeView> createdProfiles) {

    public InitialProfilesCreatedResult {
        Objects.requireNonNull(userId, "User id must not be null");
        createdProfiles = Set.copyOf(Objects.requireNonNull(createdProfiles, "Created profiles must not be null"));
        if (createdProfiles.isEmpty()) {
            throw new IllegalArgumentException("At least one profile must be created");
        }
    }
}
