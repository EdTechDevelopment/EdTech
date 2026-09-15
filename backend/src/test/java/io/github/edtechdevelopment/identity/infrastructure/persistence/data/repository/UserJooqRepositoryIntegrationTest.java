package io.github.edtechdevelopment.identity.infrastructure.persistence.data.repository;

import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityUserEmailsRecord;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityUserRolesRecord;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityUsersRecord;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.model.UserPersistenceData;
import io.github.edtechdevelopment.identity.infrastructure.persistence.exception.InvalidPersistenceDataException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class UserJooqRepositoryIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("bfc2fe41-77d6-429c-a3db-dbdddb7d18d4");
    private static final UUID SECOND_USER_ID = UUID.fromString("73650fb0-fc57-4364-9027-e673a9cd01c6");
    private static final String CURRENT_EMAIL = "anna@example.com";
    private static final String PENDING_EMAIL = "new.anna@example.com";
    private static final Instant CREATED_AT = Instant.parse("2026-09-15T14:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-09-15T14:05:00Z");

    private final UserJooqRepository repository;

    @Autowired
    UserJooqRepositoryIntegrationTest(UserJooqRepository repository) {
        this.repository = repository;
    }

    @Test
    void savesAndLoadsAllUserPersistenceRecords() {
        repository.save(userData(USER_ID, CURRENT_EMAIL, PENDING_EMAIL, List.of("STUDENT", "TEACHER")));

        UserPersistenceData loadedData = repository.findById(USER_ID).orElseThrow();

        assertAll(
                () -> assertEquals(USER_ID, loadedData.user().getId()),
                () -> assertEquals("Anna", loadedData.user().getFirstName()),
                () -> assertEquals("ACTIVE", loadedData.user().getStatus()),
                () -> assertEquals(Set.of(CURRENT_EMAIL, PENDING_EMAIL), emails(loadedData)),
                () -> assertEquals(Set.of("STUDENT", "TEACHER"), roles(loadedData)),
                () -> assertEquals(Optional.empty(), repository.findById(SECOND_USER_ID))
        );
    }

    @Test
    void loadsAllUserPersistenceRecordsForUpdate() {
        repository.save(userData(USER_ID, CURRENT_EMAIL, PENDING_EMAIL, List.of("STUDENT", "TEACHER")));

        UserPersistenceData lockedData = repository.findByIdForUpdate(USER_ID).orElseThrow();

        assertAll(
                () -> assertEquals(USER_ID, lockedData.user().getId()),
                () -> assertEquals(Set.of(CURRENT_EMAIL, PENDING_EMAIL), emails(lockedData)),
                () -> assertEquals(Set.of("STUDENT", "TEACHER"), roles(lockedData))
        );
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void requiresTransactionForLockingReadAndSave() {
        UserPersistenceData data = userData(USER_ID, CURRENT_EMAIL, null, List.of("STUDENT"));

        assertAll(
                () -> assertThrows(
                        IllegalTransactionStateException.class,
                        () -> repository.findByIdForUpdate(USER_ID)
                ),
                () -> assertThrows(
                        IllegalTransactionStateException.class,
                        () -> repository.save(data)
                )
        );
    }

    @Test
    void synchronizesChangedEmailAndRoles() {
        repository.save(userData(USER_ID, CURRENT_EMAIL, PENDING_EMAIL, List.of("STUDENT", "TEACHER")));

        UserPersistenceData confirmedEmailData = userData(USER_ID, PENDING_EMAIL, null, List.of("TEACHER"));
        confirmedEmailData.user().setFirstName("Anna-Maria");
        confirmedEmailData.user().setCreatedAt(toOffsetDateTime(CREATED_AT.plusSeconds(60)));
        repository.save(confirmedEmailData);

        UserPersistenceData loadedData = repository.findById(USER_ID).orElseThrow();

        assertAll(
                () -> assertEquals("Anna-Maria", loadedData.user().getFirstName()),
                () -> assertEquals(CREATED_AT, loadedData.user().getCreatedAt().toInstant()),
                () -> assertEquals(1, loadedData.emails().size()),
                () -> assertEquals(PENDING_EMAIL, loadedData.emails().getFirst().getEmail()),
                () -> assertEquals("CURRENT", loadedData.emails().getFirst().getKind()),
                () -> assertEquals(Set.of("TEACHER"), roles(loadedData))
        );
    }

    @Test
    void searchesOnlyByCurrentEmailButReservesBothEmailKinds() {
        repository.save(userData(USER_ID, CURRENT_EMAIL, PENDING_EMAIL, List.of("STUDENT")));

        assertAll(
                () -> assertTrue(repository.findByCurrentEmail(CURRENT_EMAIL).isPresent()),
                () -> assertTrue(repository.findByCurrentEmail(PENDING_EMAIL).isEmpty()),
                () -> assertTrue(repository.existsByEmail(CURRENT_EMAIL)),
                () -> assertTrue(repository.existsByEmail(PENDING_EMAIL)),
                () -> assertFalse(repository.existsByEmail("free@example.com"))
        );
    }

    @Test
    void reportsDuplicateEmailAsDuplicateKeyException() {
        repository.save(userData(USER_ID, CURRENT_EMAIL, null, List.of("STUDENT")));

        assertThrows(
                DuplicateKeyException.class,
                () -> repository.save(userData(SECOND_USER_ID, CURRENT_EMAIL, null, List.of("TEACHER")))
        );
    }

    @Test
    void rejectsForeignEmailRecordBeforeWritingAnything() {
        UserPersistenceData invalidData = userData(USER_ID, CURRENT_EMAIL, null, List.of("STUDENT"));
        invalidData.emails().getFirst().setUserId(SECOND_USER_ID);

        assertThrows(InvalidPersistenceDataException.class, () -> repository.save(invalidData));
        assertTrue(repository.findById(USER_ID).isEmpty());
    }

    private static UserPersistenceData userData(
            UUID userId,
            String currentEmail,
            String pendingEmail,
            List<String> roles
    ) {
        IdentityUsersRecord userRecord = new IdentityUsersRecord(
                userId,
                "stored-password-hash",
                "Anna",
                "Petrova",
                "ACTIVE",
                toOffsetDateTime(UPDATED_AT),
                toOffsetDateTime(CREATED_AT),
                toOffsetDateTime(UPDATED_AT)
        );

        List<IdentityUserEmailsRecord> emailRecords = new ArrayList<>();
        emailRecords.add(new IdentityUserEmailsRecord(userId, currentEmail, "CURRENT"));
        if (pendingEmail != null) {
            emailRecords.add(new IdentityUserEmailsRecord(userId, pendingEmail, "PENDING"));
        }

        List<IdentityUserRolesRecord> roleRecords = roles.stream()
                .map(role -> new IdentityUserRolesRecord(userId, role))
                .toList();

        return new UserPersistenceData(userRecord, emailRecords, roleRecords);
    }

    private static Set<String> emails(UserPersistenceData data) {
        return data.emails().stream()
                .map(IdentityUserEmailsRecord::getEmail)
                .collect(Collectors.toSet());
    }

    private static Set<String> roles(UserPersistenceData data) {
        return data.roles().stream()
                .map(IdentityUserRolesRecord::getRole)
                .collect(Collectors.toSet());
    }

    private static OffsetDateTime toOffsetDateTime(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
