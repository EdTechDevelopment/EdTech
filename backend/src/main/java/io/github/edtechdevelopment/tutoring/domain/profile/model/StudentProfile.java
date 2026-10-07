package io.github.edtechdevelopment.tutoring.domain.profile.model;

import io.github.edtechdevelopment.tutoring.domain.profile.exception.InvalidProfileNameException;
import io.github.edtechdevelopment.tutoring.domain.subject.model.SubjectCode;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class StudentProfile {

    private final UUID userId;
    private String displayName;
    private ProfileEmail contactEmail;
    private List<String> contactDetails;
    private Set<SubjectCode> subjectCodes;
    private URI photoUrl;

    private StudentProfile(
            UUID userId, String displayName, ProfileEmail contactEmail, List<String> contactDetails,
            Set<SubjectCode> subjectCodes, URI photoUrl) {

        this.userId = Objects.requireNonNull(userId, "User id must not be null");
        this.displayName = normalizeName(displayName);
        this.contactEmail = Objects.requireNonNull(contactEmail, "Contact email must not be null");
        this.contactDetails = List.copyOf(Objects.requireNonNull(contactDetails, "Contact details must not be null"));
        this.subjectCodes = Set.copyOf(Objects.requireNonNull(subjectCodes, "Subject codes must not be null"));
        this.photoUrl = photoUrl;
    }

    public static StudentProfile create(
            UUID userId, String displayName, ProfileEmail contactEmail, List<String> contactDetails,
            Set<SubjectCode> subjectCodes, URI photoUrl) {

        return new StudentProfile(userId, displayName, contactEmail, contactDetails, subjectCodes, photoUrl);
    }

    public static StudentProfile reconstitute(
            UUID userId, String displayName, ProfileEmail contactEmail, List<String> contactDetails,
            Set<SubjectCode> subjectCodes, URI photoUrl) {

        return new StudentProfile(userId, displayName, contactEmail, contactDetails, subjectCodes, photoUrl);
    }

    public void updateDetails(String displayName, List<String> contactDetails, URI photoUrl) {
        String newName = normalizeName(displayName);
        List<String> newContactDetails = List.copyOf(Objects.requireNonNull(contactDetails, "Contact details must not be null"));

        this.displayName = newName;
        this.contactDetails = newContactDetails;
        this.photoUrl = photoUrl;
    }

    public void changeSubjects(Set<SubjectCode> subjectCodes) {
        this.subjectCodes = Set.copyOf(Objects.requireNonNull(subjectCodes, "Subject codes must not be null"));
    }

    public boolean changeContactEmail(ProfileEmail newEmail) {
        Objects.requireNonNull(newEmail, "Contact email must not be null");
        if (contactEmail.equals(newEmail)) {
            return false;
        }
        contactEmail = newEmail;
        return true;
    }

    public UUID userId() {
        return userId;
    }

    public String displayName() {
        return displayName;
    }

    public ProfileEmail contactEmail() {
        return contactEmail;
    }

    public List<String> contactDetails() {
        return contactDetails;
    }

    public Set<SubjectCode> subjectCodes() {
        return subjectCodes;
    }

    public URI photoUrl() {
        return photoUrl;
    }

    private static String normalizeName(String name) {
        Objects.requireNonNull(name, "Display name must not be null");
        String normalized = name.strip();
        if (normalized.isEmpty()) {
            throw new InvalidProfileNameException("Display name must not be blank");
        }
        return normalized;
    }
}
