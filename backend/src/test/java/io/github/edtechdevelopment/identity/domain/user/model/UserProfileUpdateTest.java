package io.github.edtechdevelopment.identity.domain.user.model;

import io.github.edtechdevelopment.identity.domain.user.event.UserAccountUpdatedDomainEvent;
import io.github.edtechdevelopment.identity.domain.user.exception.InvalidUserDataException;
import io.github.edtechdevelopment.identity.domain.user.exception.InvalidUserStateException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserProfileUpdateTest {

    private static final UUID USER_ID = UUID.fromString("25ca25ce-c1c7-4d4a-8cf6-ffb72618ad19");
    private static final Email EMAIL = new Email("anna@example.com");
    private static final PasswordHash PASSWORD_HASH = new PasswordHash("stored-password-hash");
    private static final Instant REGISTERED_AT = Instant.parse("2026-09-14T10:00:00Z");
    private static final Instant ACTIVATED_AT = REGISTERED_AT.plusSeconds(60);
    private static final Instant CHANGED_AT = ACTIVATED_AT.plusSeconds(60);

    @Test
    void updatesFirstAndLastName() {
        User user = activeUser();

        user.updateProfile("Anna-Maria", "Smirnova", CHANGED_AT);

        assertEquals("Anna-Maria", user.firstName());
        assertEquals("Smirnova", user.lastName());
        assertEquals(CHANGED_AT, user.updatedAt());

        UserAccountUpdatedDomainEvent event = onlyUpdateEvent(user);
        assertEquals(Set.of("firstName", "lastName"), event.changedFields());
        assertEquals(CHANGED_AT, event.occurredAt());
    }

    @Test
    void updatesOnlyFirstName() {
        User user = activeUser();

        user.updateProfile("Anna-Maria", null, CHANGED_AT);

        assertEquals("Anna-Maria", user.firstName());
        assertEquals("Petrova", user.lastName());
        assertEquals(Set.of("firstName"), onlyUpdateEvent(user).changedFields());
    }

    @Test
    void updatesOnlyLastName() {
        User user = activeUser();

        user.updateProfile(null, "Smirnova", CHANGED_AT);

        assertEquals("Anna", user.firstName());
        assertEquals("Smirnova", user.lastName());
        assertEquals(Set.of("lastName"), onlyUpdateEvent(user).changedFields());
    }

    @Test
    void stripsNamesBeforeSaving() {
        User user = activeUser();

        user.updateProfile("  Anna-Maria  ", "  Smirnova  ", CHANGED_AT);

        assertEquals("Anna-Maria", user.firstName());
        assertEquals("Smirnova", user.lastName());
    }

    @Test
    void doesNothingWhenNormalizedNamesHaveNotChanged() {
        User user = activeUser();

        user.updateProfile("  Anna  ", "Petrova", CHANGED_AT);

        assertEquals(ACTIVATED_AT, user.updatedAt());
        assertTrue(user.pullDomainEvents().isEmpty());
    }

    @Test
    void rejectsUpdateWithoutFields() {
        User user = activeUser();

        assertThrows(
                InvalidUserDataException.class,
                () -> user.updateProfile(null, null, CHANGED_AT));
    }

    @Test
    void rejectsBlankAndTooLongNames() {
        User user = activeUser();
        String nameOverLimit = "a".repeat(101);

        assertThrows(
                InvalidUserDataException.class,
                () -> user.updateProfile("   ", null, CHANGED_AT));
        assertThrows(
                InvalidUserDataException.class,
                () -> user.updateProfile(null, nameOverLimit, CHANGED_AT));
    }

    @Test
    void doesNotPartiallyUpdateProfileWhenOneNameIsInvalid() {
        User user = activeUser();

        assertThrows(
                InvalidUserDataException.class,
                () -> user.updateProfile("Anna-Maria", "   ", CHANGED_AT));

        assertEquals("Anna", user.firstName());
        assertEquals("Petrova", user.lastName());
        assertEquals(ACTIVATED_AT, user.updatedAt());
        assertTrue(user.pullDomainEvents().isEmpty());
    }

    @Test
    void rejectsProfileUpdateForNonActiveUser() {
        User user = pendingUser();

        assertThrows(
                InvalidUserStateException.class,
                () -> user.updateProfile("Anna-Maria", null, CHANGED_AT));
    }

    @Test
    void rejectsChangeTimeBeforeLastUpdate() {
        User user = activeUser();

        assertThrows(
                InvalidUserDataException.class,
                () -> user.updateProfile("Anna-Maria", null, ACTIVATED_AT.minusNanos(1)));
    }

    private static User pendingUser() {
        User user = User.register(
                USER_ID,
                EMAIL,
                PASSWORD_HASH,
                "Anna",
                "Petrova",
                LocalDate.of(2000, 1, 1),
                Set.of(UserRole.STUDENT),
                REGISTERED_AT);
        user.pullDomainEvents();
        return user;
    }

    private static User activeUser() {
        User user = pendingUser();
        user.verifyRegistrationEmail(EMAIL, ACTIVATED_AT);
        user.pullDomainEvents();
        return user;
    }

    private static UserAccountUpdatedDomainEvent onlyUpdateEvent(User user) {
        var events = user.pullDomainEvents();
        assertEquals(1, events.size());
        return assertInstanceOf(UserAccountUpdatedDomainEvent.class, events.getFirst());
    }
}
