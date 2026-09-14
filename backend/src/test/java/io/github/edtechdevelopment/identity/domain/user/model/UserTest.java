package io.github.edtechdevelopment.identity.domain.user.model;

import io.github.edtechdevelopment.identity.domain.user.event.UserRegisteredDomainEvent;
import io.github.edtechdevelopment.identity.domain.user.exception.InvalidUserDataException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserTest {

    private static final UUID USER_ID = UUID.fromString("25ca25ce-c1c7-4d4a-8cf6-ffb72618ad19");
    private static final Email EMAIL = new Email("anna@example.com");
    private static final PasswordHash PASSWORD_HASH = new PasswordHash("stored-password-hash");
    private static final Instant REGISTERED_AT = Instant.parse("2026-09-13T12:00:00Z");

    @Test
    void registersPendingUserWithNormalizedNames() {
        User user = User.register(
                USER_ID,
                EMAIL,
                PASSWORD_HASH,
                "  Anna  ",
                "  Petrova  ",
                Set.of(UserRole.STUDENT),
                REGISTERED_AT
        );

        assertAll(
                () -> assertEquals(USER_ID, user.id()),
                () -> assertEquals(EMAIL, user.email()),
                () -> assertTrue(user.pendingEmail().isEmpty()),
                () -> assertEquals(PASSWORD_HASH, user.passwordHash()),
                () -> assertEquals("Anna", user.firstName()),
                () -> assertEquals("Petrova", user.lastName()),
                () -> assertEquals(Set.of(UserRole.STUDENT), user.roles()),
                () -> assertEquals(UserStatus.PENDING_EMAIL_VERIFICATION, user.status()),
                () -> assertTrue(user.emailVerifiedAt().isEmpty()),
                () -> assertEquals(REGISTERED_AT, user.createdAt()),
                () -> assertEquals(REGISTERED_AT, user.updatedAt())
        );
    }

    @Test
    void createsRegistrationEventThatCanBePulledOnlyOnce() {
        User user = registeredUser();

        var events = user.pullDomainEvents();

        assertEquals(1, events.size());
        UserRegisteredDomainEvent event = assertInstanceOf(
                UserRegisteredDomainEvent.class,
                events.getFirst()
        );
        assertAll(
                () -> assertEquals(USER_ID, event.userId()),
                () -> assertEquals(EMAIL, event.email()),
                () -> assertEquals(REGISTERED_AT, event.occurredAt()),
                () -> assertTrue(user.pullDomainEvents().isEmpty())
        );
    }

    @Test
    void protectsRolesFromExternalModification() {
        Set<UserRole> sourceRoles = EnumSet.of(UserRole.STUDENT);
        User user = User.register(
                USER_ID,
                EMAIL,
                PASSWORD_HASH,
                "Anna",
                "Petrova",
                sourceRoles,
                REGISTERED_AT
        );

        sourceRoles.add(UserRole.TEACHER);

        assertEquals(Set.of(UserRole.STUDENT), user.roles());
        assertThrows(UnsupportedOperationException.class, () -> user.roles().add(UserRole.TEACHER));
    }

    @Test
    void reconstitutesExistingUserWithoutCreatingEvents() {
        Email pendingEmail = new Email("new.anna@example.com");
        Instant verifiedAt = REGISTERED_AT.plusSeconds(300);
        Instant updatedAt = REGISTERED_AT.plusSeconds(600);

        User user = User.reconstitute(
                USER_ID,
                EMAIL,
                pendingEmail,
                PASSWORD_HASH,
                "Anna",
                "Petrova",
                Set.of(UserRole.TEACHER, UserRole.STUDENT),
                UserStatus.ACTIVE,
                verifiedAt,
                REGISTERED_AT,
                updatedAt
        );

        assertAll(
                () -> assertEquals(pendingEmail, user.pendingEmail().orElseThrow()),
                () -> assertEquals(Set.of(UserRole.TEACHER, UserRole.STUDENT), user.roles()),
                () -> assertEquals(UserStatus.ACTIVE, user.status()),
                () -> assertEquals(verifiedAt, user.emailVerifiedAt().orElseThrow()),
                () -> assertEquals(updatedAt, user.updatedAt()),
                () -> assertTrue(user.pullDomainEvents().isEmpty())
        );
    }

    @Test
    void rejectsMissingRegistrationData() {
        assertAll(
                () -> assertThrows(
                        InvalidUserDataException.class,
                        () -> User.register(null, EMAIL, PASSWORD_HASH, "Anna", "Petrova", Set.of(UserRole.STUDENT), REGISTERED_AT)
                ),
                () -> assertThrows(
                        InvalidUserDataException.class,
                        () -> User.register(USER_ID, null, PASSWORD_HASH, "Anna", "Petrova", Set.of(UserRole.STUDENT), REGISTERED_AT)
                ),
                () -> assertThrows(
                        InvalidUserDataException.class,
                        () -> User.register(USER_ID, EMAIL, null, "Anna", "Petrova", Set.of(UserRole.STUDENT), REGISTERED_AT)
                ),
                () -> assertThrows(
                        InvalidUserDataException.class,
                        () -> User.register(USER_ID, EMAIL, PASSWORD_HASH, "Anna", "Petrova", Set.of(), REGISTERED_AT)
                ),
                () -> assertThrows(
                        InvalidUserDataException.class,
                        () -> User.register(USER_ID, EMAIL, PASSWORD_HASH, "Anna", "Petrova", Set.of(UserRole.STUDENT), null)
                )
        );
    }

    @Test
    void rejectsInvalidNames() {
        String nameOverLimit = "a".repeat(101);

        assertAll(
                () -> assertThrows(
                        InvalidUserDataException.class,
                        () -> User.register(USER_ID, EMAIL, PASSWORD_HASH, null, "Petrova", Set.of(UserRole.STUDENT), REGISTERED_AT)
                ),
                () -> assertThrows(
                        InvalidUserDataException.class,
                        () -> User.register(USER_ID, EMAIL, PASSWORD_HASH, "   ", "Petrova", Set.of(UserRole.STUDENT), REGISTERED_AT)
                ),
                () -> assertThrows(
                        InvalidUserDataException.class,
                        () -> User.register(USER_ID, EMAIL, PASSWORD_HASH, nameOverLimit, "Petrova", Set.of(UserRole.STUDENT), REGISTERED_AT)
                ),
                () -> assertThrows(
                        InvalidUserDataException.class,
                        () -> User.register(USER_ID, EMAIL, PASSWORD_HASH, "Anna", "\t", Set.of(UserRole.STUDENT), REGISTERED_AT)
                )
        );
    }

    @Test
    void rejectsRoleSetContainingNull() {
        Set<UserRole> roles = new HashSet<>();
        roles.add(null);

        assertThrows(
                InvalidUserDataException.class,
                () -> User.register(USER_ID, EMAIL, PASSWORD_HASH, "Anna", "Petrova", roles, REGISTERED_AT)
        );
    }

    @Test
    void rejectsPendingEmailEqualToCurrentEmail() {
        assertThrows(
                InvalidUserDataException.class,
                () -> User.reconstitute(
                        USER_ID,
                        EMAIL,
                        EMAIL,
                        PASSWORD_HASH,
                        "Anna",
                        "Petrova",
                        Set.of(UserRole.STUDENT),
                        UserStatus.ACTIVE,
                        REGISTERED_AT,
                        REGISTERED_AT,
                        REGISTERED_AT
                )
        );
    }

    @Test
    void rejectsInconsistentTimestamps() {
        Instant beforeRegistration = REGISTERED_AT.minusSeconds(1);

        assertAll(
                () -> assertThrows(
                        InvalidUserDataException.class,
                        () -> User.reconstitute(
                                USER_ID,
                                EMAIL,
                                null,
                                PASSWORD_HASH,
                                "Anna",
                                "Petrova",
                                Set.of(UserRole.STUDENT),
                                UserStatus.ACTIVE,
                                REGISTERED_AT,
                                REGISTERED_AT,
                                beforeRegistration
                        )
                ),
                () -> assertThrows(
                        InvalidUserDataException.class,
                        () -> User.reconstitute(
                                USER_ID,
                                EMAIL,
                                null,
                                PASSWORD_HASH,
                                "Anna",
                                "Petrova",
                                Set.of(UserRole.STUDENT),
                                UserStatus.ACTIVE,
                                beforeRegistration,
                                REGISTERED_AT,
                                REGISTERED_AT
                        )
                )
        );
    }

    private static User registeredUser() {
        return User.register(
                USER_ID,
                EMAIL,
                PASSWORD_HASH,
                "Anna",
                "Petrova",
                Set.of(UserRole.STUDENT),
                REGISTERED_AT
        );
    }
}
