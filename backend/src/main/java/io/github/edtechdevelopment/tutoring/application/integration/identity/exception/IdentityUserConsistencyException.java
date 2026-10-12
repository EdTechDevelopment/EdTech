package io.github.edtechdevelopment.tutoring.application.integration.identity.exception;

import io.github.edtechdevelopment.tutoring.domain.profile.model.ProfileType;

import java.util.Objects;
import java.util.UUID;

public final class IdentityUserConsistencyException extends RuntimeException {

    private final UUID userId;
    private final ProfileType profileType;

    public IdentityUserConsistencyException(UUID userId, ProfileType profileType) {
        super("Profile creation requires an existing account with the corresponding role");
        this.userId = Objects.requireNonNull(userId, "User id must not be null");
        this.profileType = Objects.requireNonNull(profileType, "Profile type must not be null");
    }

    public UUID userId() {
        return userId;
    }

    public ProfileType profileType() {
        return profileType;
    }
}
