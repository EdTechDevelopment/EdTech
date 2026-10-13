package io.github.edtechdevelopment.identity.infrastructure.persistence.mapper;

import io.github.edtechdevelopment.identity.domain.user.model.Email;
import io.github.edtechdevelopment.identity.domain.user.model.PasswordHash;
import io.github.edtechdevelopment.identity.domain.user.model.User;
import io.github.edtechdevelopment.identity.domain.user.model.UserRole;
import io.github.edtechdevelopment.identity.domain.user.model.UserStatus;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityUserEmailsRecord;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityUserRolesRecord;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityUsersRecord;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.model.UserPersistenceData;
import io.github.edtechdevelopment.identity.infrastructure.persistence.exception.InvalidPersistenceDataException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserPersistenceMapperTest {

    private static final UUID USER_ID = UUID.fromString("fa980561-e9ae-4c80-879d-c34449e55af7");
    private static final UUID ANOTHER_USER_ID = UUID.fromString("ed355991-aa90-4972-b37f-c59cb1b6ce3d");
    private static final Email CURRENT_EMAIL = new Email("anna@example.com");
    private static final Email PENDING_EMAIL = new Email("new.anna@example.com");
    private static final PasswordHash PASSWORD_HASH = new PasswordHash("stored-password-hash");
    private static final Instant CREATED_AT = Instant.parse("2026-09-15T10:00:00Z");
    private static final Instant VERIFIED_AT = Instant.parse("2026-09-15T10:05:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-09-15T10:10:00Z");

    private final UserPersistenceMapper mapper = new UserPersistenceMapper();

    @Test
    void mapsActiveUserToPersistenceDataAndBackWithoutLosingData() {
        User originalUser = activeUser(null);

        UserPersistenceData persistenceData = mapper.toPersistence(originalUser);
        User restoredUser = mapper.toDomain(persistenceData);

        assertAll(
                () -> assertEquals(originalUser.id(), restoredUser.id()),
                () -> assertEquals(originalUser.email(), restoredUser.email()),
                () -> assertTrue(restoredUser.pendingEmail().isEmpty()),
                () -> assertEquals(originalUser.passwordHash(), restoredUser.passwordHash()),
                () -> assertEquals(originalUser.firstName(), restoredUser.firstName()),
                () -> assertEquals(originalUser.lastName(), restoredUser.lastName()),
                () -> assertEquals(originalUser.birthDate(), restoredUser.birthDate()),
                () -> assertEquals(originalUser.roles(), restoredUser.roles()),
                () -> assertEquals(originalUser.status(), restoredUser.status()),
                () -> assertEquals(originalUser.emailVerifiedAt(), restoredUser.emailVerifiedAt()),
                () -> assertEquals(originalUser.createdAt(), restoredUser.createdAt()),
                () -> assertEquals(originalUser.updatedAt(), restoredUser.updatedAt())
        );
    }

    @Test
    void mapsPendingEmailToSeparatePersistenceRecord() {
        UserPersistenceData persistenceData = mapper.toPersistence(activeUser(PENDING_EMAIL));

        assertEquals(2, persistenceData.emails().size());
        assertTrue(persistenceData.emails().stream().anyMatch(record ->
                record.getKind().equals("CURRENT") && record.getEmail().equals(CURRENT_EMAIL.value())
        ));
        assertTrue(persistenceData.emails().stream().anyMatch(record ->
                record.getKind().equals("PENDING") && record.getEmail().equals(PENDING_EMAIL.value())
        ));
    }

    @Test
    void reconstitutesUserWithoutCreatingDomainEvents() {
        User restoredUser = mapper.toDomain(validPersistenceData());

        assertTrue(restoredUser.pullDomainEvents().isEmpty());
    }

    @Test
    void rejectsPersistenceDataWithoutCurrentEmail() {
        UserPersistenceData data = new UserPersistenceData(
                userRecord(),
                List.of(new IdentityUserEmailsRecord(USER_ID, PENDING_EMAIL.value(), "PENDING")),
                List.of(new IdentityUserRolesRecord(USER_ID, UserRole.STUDENT.name()))
        );

        InvalidPersistenceDataException exception = assertThrows(
                InvalidPersistenceDataException.class,
                () -> mapper.toDomain(data)
        );

        assertEquals("User must have exactly one current email", exception.getMessage());
    }

    @Test
    void rejectsEmailAndRoleRecordsBelongingToAnotherUser() {
        UserPersistenceData foreignEmailData = new UserPersistenceData(
                userRecord(),
                List.of(new IdentityUserEmailsRecord(ANOTHER_USER_ID, CURRENT_EMAIL.value(), "CURRENT")),
                List.of(new IdentityUserRolesRecord(USER_ID, UserRole.STUDENT.name()))
        );
        UserPersistenceData foreignRoleData = new UserPersistenceData(
                userRecord(),
                List.of(new IdentityUserEmailsRecord(USER_ID, CURRENT_EMAIL.value(), "CURRENT")),
                List.of(new IdentityUserRolesRecord(ANOTHER_USER_ID, UserRole.STUDENT.name()))
        );

        assertAll(
                () -> assertThrows(InvalidPersistenceDataException.class, () -> mapper.toDomain(foreignEmailData)),
                () -> assertThrows(InvalidPersistenceDataException.class, () -> mapper.toDomain(foreignRoleData))
        );
    }

    private static User activeUser(Email pendingEmail) {
        return User.reconstitute(
                USER_ID,
                CURRENT_EMAIL,
                pendingEmail,
                PASSWORD_HASH,
                "Anna",
                "Petrova",
                LocalDate.of(2000, 1, 1),
                Set.of(UserRole.STUDENT, UserRole.TEACHER),
                UserStatus.ACTIVE,
                VERIFIED_AT,
                CREATED_AT,
                UPDATED_AT
        );
    }

    private static UserPersistenceData validPersistenceData() {
        return new UserPersistenceData(
                userRecord(),
                List.of(new IdentityUserEmailsRecord(USER_ID, CURRENT_EMAIL.value(), "CURRENT")),
                List.of(new IdentityUserRolesRecord(USER_ID, UserRole.STUDENT.name()))
        );
    }

    private static IdentityUsersRecord userRecord() {
        return new IdentityUsersRecord(
                USER_ID,
                PASSWORD_HASH.value(),
                "Anna",
                "Petrova",
                UserStatus.ACTIVE.name(),
                OffsetDateTime.ofInstant(VERIFIED_AT, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(CREATED_AT, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(UPDATED_AT, ZoneOffset.UTC),
                LocalDate.of(2000, 1, 1)
        );
    }
}
