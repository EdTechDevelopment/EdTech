package io.github.edtechdevelopment.tutoring.api.exception;

import io.github.edtechdevelopment.tutoring.api.model.profile.ProfileTypeView;

import java.util.Objects;
import java.util.UUID;

public final class ProfileNotFoundException extends RuntimeException {

    private final ProfileTypeView profileType;
    private final UUID userId;

    public ProfileNotFoundException(ProfileTypeView profileType, UUID userId) {
        super("Profile does not exist");
        this.profileType = Objects.requireNonNull(profileType, "Profile type must not be null");
        this.userId = Objects.requireNonNull(userId, "User id must not be null");
    }

    public ProfileTypeView profileType() {
        return profileType;
    }

    public UUID userId() {
        return userId;
    }
}
