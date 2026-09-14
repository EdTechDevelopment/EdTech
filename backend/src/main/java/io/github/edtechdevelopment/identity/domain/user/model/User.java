package io.github.edtechdevelopment.identity.domain.user.model;

import io.github.edtechdevelopment.identity.domain.user.event.UserAccountUpdatedDomainEvent;
import io.github.edtechdevelopment.identity.domain.user.event.UserActivatedDomainEvent;
import io.github.edtechdevelopment.identity.domain.user.event.UserDomainEvent;
import io.github.edtechdevelopment.identity.domain.user.event.UserRegisteredDomainEvent;
import io.github.edtechdevelopment.identity.domain.user.exception.InvalidUserDataException;
import io.github.edtechdevelopment.identity.domain.user.exception.InvalidUserStateException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class User {

    private static final int MAX_NAME_LENGTH = 100;
    private static final String EMAIL_FIELD = "email";
    private static final String PENDING_EMAIL_FIELD = "pendingEmail";
    private static final String EMAIL_VERIFIED_AT_FIELD = "emailVerifiedAt";
    private static final String FIRST_NAME_FIELD = "firstName";
    private static final String LAST_NAME_FIELD = "lastName";

    private final UUID id;
    private Email email;
    private Email pendingEmail;
    private PasswordHash passwordHash;
    private String firstName;
    private String lastName;
    private final Set<UserRole> roles;
    private UserStatus status;
    private Instant emailVerifiedAt;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<UserDomainEvent> domainEvents;

    private User(
            UUID id, Email email, Email pendingEmail, PasswordHash passwordHash, String firstName, String lastName,
            Set<UserRole> roles, UserStatus status, Instant emailVerifiedAt, Instant createdAt, Instant updatedAt) {

        this.id = requireValue(id, "User id must not be null");
        this.email = requireValue(email, "Email must not be null");
        this.pendingEmail = validatePendingEmail(email, pendingEmail);
        this.passwordHash = requireValue(passwordHash, "Password hash must not be null");
        this.firstName = normalizeName(firstName, "First name");
        this.lastName = normalizeName(lastName, "Last name");
        this.roles = copyRoles(roles);
        this.status = requireValue(status, "User status must not be null");
        this.emailVerifiedAt = emailVerifiedAt;
        this.createdAt = requireValue(createdAt, "Creation time must not be null");
        this.updatedAt = requireValue(updatedAt, "Update time must not be null");
        validateTimestamps(this.createdAt, this.updatedAt, this.emailVerifiedAt);
        this.domainEvents = new ArrayList<>();
    }

    public static User register(
            UUID id, Email email, PasswordHash passwordHash, String firstName,
            String lastName, Set<UserRole> roles, Instant registeredAt) {

        User user = new User(
                id, email, null, passwordHash, firstName, lastName,
                roles, UserStatus.PENDING_EMAIL_VERIFICATION, null, registeredAt, registeredAt);

        user.domainEvents.add(new UserRegisteredDomainEvent(user.id, user.email, registeredAt));
        return user;
    }

    public static User reconstitute(
            UUID id, Email email, Email pendingEmail, PasswordHash passwordHash, String firstName, String lastName,
            Set<UserRole> roles, UserStatus status, Instant emailVerifiedAt, Instant createdAt, Instant updatedAt) {

        return new User(
                id, email, pendingEmail, passwordHash, firstName, lastName,
                roles, status, emailVerifiedAt, createdAt, updatedAt);
    }

    public void verifyRegistrationEmail(Email confirmedEmail, Instant verifiedAt) {
        requireStatus(UserStatus.PENDING_EMAIL_VERIFICATION, "Only a user awaiting email verification can be activated");
        requireValue(confirmedEmail, "Confirmed email must not be null");
        validateChangeTime(verifiedAt);

        if (!email.equals(confirmedEmail)) {
            throw new InvalidUserStateException("Confirmed email must match the current registration email");
        }

        status = UserStatus.ACTIVE;
        emailVerifiedAt = verifiedAt;
        updatedAt = verifiedAt;
        domainEvents.add(new UserActivatedDomainEvent(id, email, verifiedAt));
    }

    public void requestEmailChange(Email newEmail, Instant requestedAt) {

        requireStatus(UserStatus.ACTIVE, "Only an active user can change email");
        requireValue(newEmail, "New email must not be null");
        validateChangeTime(requestedAt);

        if (email.equals(newEmail)) {
            throw new InvalidUserDataException("New email must differ from current email");
        }
        if (newEmail.equals(pendingEmail)) {
            throw new InvalidUserDataException("New email is already pending verification");
        }

        pendingEmail = newEmail;
        updatedAt = requestedAt;
        domainEvents.add(new UserAccountUpdatedDomainEvent(id, Set.of(PENDING_EMAIL_FIELD), requestedAt));
    }

    public void confirmPendingEmail(Email confirmedEmail, Instant confirmedAt) {

        requireStatus(UserStatus.ACTIVE, "Only an active user can confirm a new email");
        requireValue(confirmedEmail, "Confirmed email must not be null");
        validateChangeTime(confirmedAt);

        if (pendingEmail == null) {
            throw new InvalidUserStateException("User has no pending email to confirm");
        }
        if (!pendingEmail.equals(confirmedEmail)) {
            throw new InvalidUserStateException("Confirmed email must match the pending email");
        }

        email = pendingEmail;
        pendingEmail = null;
        emailVerifiedAt = confirmedAt;
        updatedAt = confirmedAt;
        domainEvents.add(new UserAccountUpdatedDomainEvent(id, Set.of(EMAIL_FIELD, PENDING_EMAIL_FIELD, EMAIL_VERIFIED_AT_FIELD), confirmedAt));
    }

    public void updateProfile(String newFirstName, String newLastName, Instant changedAt) {

        requireStatus(UserStatus.ACTIVE, "Only an active user can update profile");
        if (newFirstName == null && newLastName == null) {
            throw new InvalidUserDataException("At least one profile field must be provided");
        }
        validateChangeTime(changedAt);

        String normalizedFirstName = newFirstName == null
                ? firstName
                : normalizeName(newFirstName, "First name");
        String normalizedLastName = newLastName == null
                ? lastName
                : normalizeName(newLastName, "Last name");

        Set<String> changedFields = new HashSet<>();
        if (!firstName.equals(normalizedFirstName)) {
            changedFields.add(FIRST_NAME_FIELD);
        }
        if (!lastName.equals(normalizedLastName)) {
            changedFields.add(LAST_NAME_FIELD);
        }

        if (changedFields.isEmpty()) {
            return;
        }

        firstName = normalizedFirstName;
        lastName = normalizedLastName;
        updatedAt = changedAt;
        domainEvents.add(new UserAccountUpdatedDomainEvent(id, changedFields, changedAt));
    }

    public UUID id() {
        return id;
    }

    public Email email() {
        return email;
    }

    public Optional<Email> pendingEmail() {
        return Optional.ofNullable(pendingEmail);
    }

    public PasswordHash passwordHash() {
        return passwordHash;
    }

    public String firstName() {
        return firstName;
    }

    public String lastName() {
        return lastName;
    }

    public Set<UserRole> roles() {
        return Set.copyOf(roles);
    }

    public UserStatus status() {
        return status;
    }

    public Optional<Instant> emailVerifiedAt() {
        return Optional.ofNullable(emailVerifiedAt);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public List<UserDomainEvent> pullDomainEvents() {
        List<UserDomainEvent> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    private static Email validatePendingEmail(Email email, Email pendingEmail) {
        if (email.equals(pendingEmail)) {
            throw new InvalidUserDataException("Pending email must differ from current email");
        }
        return pendingEmail;
    }

    private static String normalizeName(String name, String fieldName) {
        if (name == null) {
            throw new InvalidUserDataException(fieldName + " must not be null");
        }

        String normalizedName = name.strip();
        if (normalizedName.isEmpty()) {
            throw new InvalidUserDataException(fieldName + " must not be blank");
        }
        if (normalizedName.length() > MAX_NAME_LENGTH) {
            throw new InvalidUserDataException(fieldName + " must not exceed 100 characters");
        }
        return normalizedName;
    }

    private static Set<UserRole> copyRoles(Set<UserRole> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new InvalidUserDataException("User must have at least one role");
        }
        if (roles.stream().anyMatch(Objects::isNull)) {
            throw new InvalidUserDataException("User roles must not contain null");
        }
        return EnumSet.copyOf(roles);
    }

    private static void validateTimestamps(Instant createdAt, Instant updatedAt, Instant emailVerifiedAt) {

        if (updatedAt.isBefore(createdAt)) {
            throw new InvalidUserDataException("Update time must not be before creation time");
        }
        if (emailVerifiedAt != null && emailVerifiedAt.isBefore(createdAt)) {
            throw new InvalidUserDataException("Email verification time must not be before creation time");
        }
    }

    private void requireStatus(UserStatus requiredStatus, String message) {

        if (status != requiredStatus) {
            throw new InvalidUserStateException(message);
        }
    }

    private void validateChangeTime(Instant changeTime) {
        requireValue(changeTime, "Change time must not be null");
        if (changeTime.isBefore(updatedAt)) {
            throw new InvalidUserDataException("Change time must not be before the last update time");
        }
    }

    private static <T> T requireValue(T value, String message) {

        if (value == null) {
            throw new InvalidUserDataException(message);
        }
        return value;
    }
}
