package io.github.edtechdevelopment.identity.domain.user.model;

import io.github.edtechdevelopment.identity.domain.user.event.UserAccountUpdatedDomainEvent;
import io.github.edtechdevelopment.identity.domain.user.event.UserActivatedDomainEvent;
import io.github.edtechdevelopment.identity.domain.user.exception.InvalidUserDataException;
import io.github.edtechdevelopment.identity.domain.user.exception.InvalidUserStateException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserEmailLifecycleTest {

    private static final UUID USER_ID = UUID.fromString("25ca25ce-c1c7-4d4a-8cf6-ffb72618ad19");
    private static final Email CURRENT_EMAIL = new Email("anna@example.com");
    private static final Email FIRST_PENDING_EMAIL = new Email("new.anna@example.com");
    private static final Email SECOND_PENDING_EMAIL = new Email("anna.pet@example.com");
    private static final PasswordHash PASSWORD_HASH = new PasswordHash("stored-password-hash");
    private static final Instant REGISTERED_AT = Instant.parse("2026-09-14T10:00:00Z");
    private static final Instant VERIFIED_AT = REGISTERED_AT.plusSeconds(60);

    @Test
    void verifiesRegistrationEmailAndActivatesUser() {
        User user = pendingUser();

        user.verifyRegistrationEmail(CURRENT_EMAIL, VERIFIED_AT);

        assertEquals(UserStatus.ACTIVE, user.status());
        assertEquals(VERIFIED_AT, user.emailVerifiedAt().orElseThrow());
        assertEquals(VERIFIED_AT, user.updatedAt());

        var events = user.pullDomainEvents();
        assertEquals(1, events.size());
        UserActivatedDomainEvent event = assertInstanceOf(
                UserActivatedDomainEvent.class,
                events.getFirst());
        assertEquals(USER_ID, event.userId());
        assertEquals(CURRENT_EMAIL, event.email());
        assertEquals(VERIFIED_AT, event.occurredAt());
    }

    @Test
    void cannotVerifyRegistrationForAlreadyActiveUser() {
        User user = activeUser();

        assertThrows(
                InvalidUserStateException.class,
                () -> user.verifyRegistrationEmail(CURRENT_EMAIL, VERIFIED_AT.plusSeconds(1)));
    }

    @Test
    void cannotVerifyRegistrationWithDifferentEmail() {
        User user = pendingUser();

        assertThrows(
                InvalidUserStateException.class,
                () -> user.verifyRegistrationEmail(FIRST_PENDING_EMAIL, VERIFIED_AT));
    }

    @Test
    void requestsEmailChangeWithoutReplacingCurrentEmail() {
        User user = activeUser();
        Instant requestedAt = VERIFIED_AT.plusSeconds(60);

        user.requestEmailChange(FIRST_PENDING_EMAIL, requestedAt);

        assertEquals(CURRENT_EMAIL, user.email());
        assertEquals(FIRST_PENDING_EMAIL, user.pendingEmail().orElseThrow());
        assertEquals(VERIFIED_AT, user.emailVerifiedAt().orElseThrow());
        assertEquals(requestedAt, user.updatedAt());

        UserAccountUpdatedDomainEvent event = onlyAccountUpdatedEvent(user);
        assertEquals(Set.of("pendingEmail"), event.changedFields());
        assertEquals(requestedAt, event.occurredAt());
    }

    @Test
    void rejectsCurrentAndAlreadyPendingEmailAsNewEmail() {
        User user = activeUser();
        Instant requestedAt = VERIFIED_AT.plusSeconds(60);
        user.requestEmailChange(FIRST_PENDING_EMAIL, requestedAt);
        user.pullDomainEvents();

        assertThrows(
                InvalidUserDataException.class,
                () -> user.requestEmailChange(CURRENT_EMAIL, requestedAt.plusSeconds(1)));
        assertThrows(
                InvalidUserDataException.class,
                () -> user.requestEmailChange(FIRST_PENDING_EMAIL, requestedAt.plusSeconds(1)));
    }

    @Test
    void replacesPendingEmailWithAnotherEmail() {
        User user = activeUser();
        Instant firstRequestAt = VERIFIED_AT.plusSeconds(60);
        Instant secondRequestAt = firstRequestAt.plusSeconds(60);
        user.requestEmailChange(FIRST_PENDING_EMAIL, firstRequestAt);
        user.pullDomainEvents();

        user.requestEmailChange(SECOND_PENDING_EMAIL, secondRequestAt);

        assertEquals(SECOND_PENDING_EMAIL, user.pendingEmail().orElseThrow());
        assertEquals(secondRequestAt, user.updatedAt());
        assertEquals(Set.of("pendingEmail"), onlyAccountUpdatedEvent(user).changedFields());
    }

    @Test
    void confirmsPendingEmailAndKeepsUserActive() {
        User user = activeUser();
        Instant requestedAt = VERIFIED_AT.plusSeconds(60);
        Instant confirmedAt = requestedAt.plusSeconds(60);
        user.requestEmailChange(FIRST_PENDING_EMAIL, requestedAt);
        user.pullDomainEvents();

        user.confirmPendingEmail(FIRST_PENDING_EMAIL, confirmedAt);

        assertEquals(FIRST_PENDING_EMAIL, user.email());
        assertTrue(user.pendingEmail().isEmpty());
        assertEquals(UserStatus.ACTIVE, user.status());
        assertEquals(confirmedAt, user.emailVerifiedAt().orElseThrow());
        assertEquals(confirmedAt, user.updatedAt());

        UserAccountUpdatedDomainEvent event = onlyAccountUpdatedEvent(user);
        assertEquals(Set.of("email", "pendingEmail", "emailVerifiedAt"), event.changedFields());
        assertEquals(confirmedAt, event.occurredAt());
    }

    @Test
    void cannotConfirmMissingOrDifferentPendingEmail() {
        User userWithoutPendingEmail = activeUser();

        assertThrows(
                InvalidUserStateException.class,
                () -> userWithoutPendingEmail.confirmPendingEmail(FIRST_PENDING_EMAIL, VERIFIED_AT.plusSeconds(60)));

        User userWithPendingEmail = activeUser();
        Instant requestedAt = VERIFIED_AT.plusSeconds(60);
        userWithPendingEmail.requestEmailChange(FIRST_PENDING_EMAIL, requestedAt);

        assertThrows(
                InvalidUserStateException.class,
                () -> userWithPendingEmail.confirmPendingEmail(SECOND_PENDING_EMAIL, requestedAt.plusSeconds(60)));
    }

    @Test
    void rejectsChangeTimeBeforeLastUpdate() {
        User pendingUser = pendingUser();
        assertThrows(
                InvalidUserDataException.class,
                () -> pendingUser.verifyRegistrationEmail(CURRENT_EMAIL, REGISTERED_AT.minusNanos(1)));

        User activeUser = activeUser();
        assertThrows(
                InvalidUserDataException.class,
                () -> activeUser.requestEmailChange(FIRST_PENDING_EMAIL, VERIFIED_AT.minusNanos(1)));
    }

    @Test
    void protectsChangedFieldsFromExternalModification() {
        Set<String> changedFields = new HashSet<>(Set.of("email"));
        UserAccountUpdatedDomainEvent event = new UserAccountUpdatedDomainEvent(
                USER_ID,
                changedFields,
                VERIFIED_AT);

        changedFields.add("firstName");

        assertEquals(Set.of("email"), event.changedFields());
        assertThrows(
                UnsupportedOperationException.class,
                () -> event.changedFields().add("lastName"));
    }

    private static User pendingUser() {
        User user = User.register(
                USER_ID,
                CURRENT_EMAIL,
                PASSWORD_HASH,
                "Anna",
                "Petrova",
                Set.of(UserRole.STUDENT),
                REGISTERED_AT);
        user.pullDomainEvents();
        return user;
    }

    private static User activeUser() {
        User user = pendingUser();
        user.verifyRegistrationEmail(CURRENT_EMAIL, VERIFIED_AT);
        user.pullDomainEvents();
        return user;
    }

    private static UserAccountUpdatedDomainEvent onlyAccountUpdatedEvent(User user) {
        var events = user.pullDomainEvents();
        assertEquals(1, events.size());
        return assertInstanceOf(UserAccountUpdatedDomainEvent.class, events.getFirst());
    }
}
