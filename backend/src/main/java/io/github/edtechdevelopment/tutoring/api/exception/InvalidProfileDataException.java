package io.github.edtechdevelopment.tutoring.api.exception;

import io.github.edtechdevelopment.tutoring.api.model.profile.ProfileTypeView;

import java.util.Objects;
import java.util.Set;

public final class InvalidProfileDataException extends RuntimeException {

    private final ProfileTypeView profileType;
    private final Set<String> invalidFields;

    public InvalidProfileDataException(ProfileTypeView profileType, Set<String> invalidFields) {
        super("Profile data is invalid");
        this.profileType = profileType;
        this.invalidFields = Set.copyOf(Objects.requireNonNull(invalidFields, "Invalid fields must not be null"));
    }

    public ProfileTypeView profileType() {
        return profileType;
    }

    public Set<String> invalidFields() {
        return invalidFields;
    }
}
