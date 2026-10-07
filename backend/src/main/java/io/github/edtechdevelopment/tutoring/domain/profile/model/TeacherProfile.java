package io.github.edtechdevelopment.tutoring.domain.profile.model;

import io.github.edtechdevelopment.tutoring.domain.profile.exception.EmptyTeacherSubjectsException;
import io.github.edtechdevelopment.tutoring.domain.profile.exception.InvalidExperienceException;
import io.github.edtechdevelopment.tutoring.domain.profile.exception.InvalidProfileNameException;
import io.github.edtechdevelopment.tutoring.domain.subject.model.SubjectCode;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class TeacherProfile {

    private final UUID userId;
    private String displayName;
    private ProfileEmail contactEmail;
    private List<String> contactDetails;
    private Set<SubjectCode> subjectCodes;
    private String description;
    private String education;
    private Integer experienceYears;
    private String city;
    private URI photoUrl;

    private TeacherProfile(
            UUID userId, String displayName, ProfileEmail contactEmail, List<String> contactDetails,
            Set<SubjectCode> subjectCodes, String description, String education, Integer experienceYears,
            String city, URI photoUrl) {

        this.userId = Objects.requireNonNull(userId, "User id must not be null");
        this.displayName = normalizeName(displayName);
        this.contactEmail = Objects.requireNonNull(contactEmail, "Contact email must not be null");
        this.contactDetails = List.copyOf(Objects.requireNonNull(contactDetails, "Contact details must not be null"));
        this.subjectCodes = copySubjects(subjectCodes);
        this.description = description;
        this.education = education;
        this.experienceYears = validateExperience(experienceYears);
        this.city = city;
        this.photoUrl = photoUrl;
    }

    public static TeacherProfile create(
            UUID userId, String displayName, ProfileEmail contactEmail, List<String> contactDetails,
            Set<SubjectCode> subjectCodes, String description, String education, Integer experienceYears,
            String city, URI photoUrl) {

        return new TeacherProfile(userId, displayName, contactEmail, contactDetails, subjectCodes,
                description, education, experienceYears, city, photoUrl);
    }

    public static TeacherProfile reconstitute(
            UUID userId, String displayName, ProfileEmail contactEmail, List<String> contactDetails,
            Set<SubjectCode> subjectCodes, String description, String education, Integer experienceYears,
            String city, URI photoUrl) {

        return new TeacherProfile(userId, displayName, contactEmail, contactDetails, subjectCodes,
                description, education, experienceYears, city, photoUrl);
    }

    public void updateDetails(
            String displayName, List<String> contactDetails, String description, String education,
            Integer experienceYears, String city, URI photoUrl) {

        String newName = normalizeName(displayName);
        List<String> newContactDetails = List.copyOf(Objects.requireNonNull(contactDetails, "Contact details must not be null"));
        validateExperience(experienceYears);

        this.displayName = newName;
        this.contactDetails = newContactDetails;
        this.description = description;
        this.education = education;
        this.experienceYears = experienceYears;
        this.city = city;
        this.photoUrl = photoUrl;
    }

    public void changeSubjects(Set<SubjectCode> subjectCodes) {
        this.subjectCodes = copySubjects(subjectCodes);
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

    public String description() {
        return description;
    }

    public String education() {
        return education;
    }

    public Integer experienceYears() {
        return experienceYears;
    }

    public String city() {
        return city;
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

    private static Integer validateExperience(Integer experienceYears) {
        if (experienceYears != null && experienceYears < 0) {
            throw new InvalidExperienceException("Experience years must not be negative");
        }
        return experienceYears;
    }

    private static Set<SubjectCode> copySubjects(Set<SubjectCode> subjectCodes) {
        Set<SubjectCode> copy = Set.copyOf(Objects.requireNonNull(subjectCodes, "Subject codes must not be null"));
        if (copy.isEmpty()) {
            throw new EmptyTeacherSubjectsException("Teacher must select at least one subject");
        }
        return copy;
    }
}
