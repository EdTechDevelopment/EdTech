package io.github.edtechdevelopment.identity.infrastructure.persistence.data.repository;

import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityUserEmailsRecord;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityUserRolesRecord;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityUsersRecord;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.model.UserPersistenceData;
import io.github.edtechdevelopment.identity.infrastructure.persistence.exception.InvalidPersistenceDataException;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.IdentityUserEmails.IDENTITY_USER_EMAILS;
import static io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.IdentityUserRoles.IDENTITY_USER_ROLES;
import static io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.IdentityUsers.IDENTITY_USERS;

@Repository
public class UserJooqRepository {

    private static final String CURRENT_EMAIL_KIND = "CURRENT";
    private final DSLContext dslContext;

    public UserJooqRepository(DSLContext dslContext) {
        this.dslContext = Objects.requireNonNull(dslContext, "DSL context must not be null");
    }

    public Optional<UserPersistenceData> findById(UUID userId) {
        Objects.requireNonNull(userId, "User id must not be null");

        IdentityUsersRecord userRecord = dslContext
                .selectFrom(IDENTITY_USERS)
                .where(IDENTITY_USERS.ID.eq(userId))
                .fetchOne();

        return loadRelatedRecords(userRecord);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<UserPersistenceData> findByIdForUpdate(UUID userId) {
        Objects.requireNonNull(userId, "User id must not be null");

        IdentityUsersRecord userRecord = dslContext
                .selectFrom(IDENTITY_USERS)
                .where(IDENTITY_USERS.ID.eq(userId))
                .forUpdate()
                .fetchOne();

        return loadRelatedRecords(userRecord);
    }

    private Optional<UserPersistenceData> loadRelatedRecords(IdentityUsersRecord userRecord) {
        if (userRecord == null) {
            return Optional.empty();
        }

        UUID userId = userRecord.getId();
        List<IdentityUserEmailsRecord> emailRecords = dslContext
                .selectFrom(IDENTITY_USER_EMAILS)
                .where(IDENTITY_USER_EMAILS.USER_ID.eq(userId))
                .orderBy(IDENTITY_USER_EMAILS.KIND)
                .fetch();

        List<IdentityUserRolesRecord> roleRecords = dslContext
                .selectFrom(IDENTITY_USER_ROLES)
                .where(IDENTITY_USER_ROLES.USER_ID.eq(userId))
                .orderBy(IDENTITY_USER_ROLES.ROLE)
                .fetch();

        return Optional.of(new UserPersistenceData(userRecord, emailRecords, roleRecords));
    }

    public Optional<UserPersistenceData> findByCurrentEmail(String email) {
        Objects.requireNonNull(email, "Email must not be null");

        UUID userId = dslContext
                .select(IDENTITY_USER_EMAILS.USER_ID)
                .from(IDENTITY_USER_EMAILS)
                .where(IDENTITY_USER_EMAILS.EMAIL.eq(email))
                .and(IDENTITY_USER_EMAILS.KIND.eq(CURRENT_EMAIL_KIND))
                .fetchOne(IDENTITY_USER_EMAILS.USER_ID);

        return userId == null ? Optional.empty() : findById(userId);
    }

    public boolean existsByEmail(String email) {
        Objects.requireNonNull(email, "Email must not be null");

        return dslContext.fetchExists(
                dslContext
                        .selectOne()
                        .from(IDENTITY_USER_EMAILS)
                        .where(IDENTITY_USER_EMAILS.EMAIL.eq(email))
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void save(UserPersistenceData data) {
        Objects.requireNonNull(data, "User persistence data must not be null");

        IdentityUsersRecord userRecord = data.user();
        UUID userId = Objects.requireNonNull(userRecord.getId(), "User id must not be null");
        validateRelatedRecordUserIds(userId, data);

        upsertUser(userRecord);
        synchronizeEmails(userId, data.emails());
        synchronizeRoles(userId, data.roles());
    }

    private void upsertUser(IdentityUsersRecord record) {
        dslContext
                .insertInto(IDENTITY_USERS)
                .set(IDENTITY_USERS.ID, record.getId())
                .set(IDENTITY_USERS.PASSWORD_HASH, record.getPasswordHash())
                .set(IDENTITY_USERS.FIRST_NAME, record.getFirstName())
                .set(IDENTITY_USERS.LAST_NAME, record.getLastName())
                .set(IDENTITY_USERS.STATUS, record.getStatus())
                .set(IDENTITY_USERS.EMAIL_VERIFIED_AT, record.getEmailVerifiedAt())
                .set(IDENTITY_USERS.CREATED_AT, record.getCreatedAt())
                .set(IDENTITY_USERS.UPDATED_AT, record.getUpdatedAt())
                .onConflict(IDENTITY_USERS.ID)
                .doUpdate()
                .set(IDENTITY_USERS.PASSWORD_HASH, record.getPasswordHash())
                .set(IDENTITY_USERS.FIRST_NAME, record.getFirstName())
                .set(IDENTITY_USERS.LAST_NAME, record.getLastName())
                .set(IDENTITY_USERS.STATUS, record.getStatus())
                .set(IDENTITY_USERS.EMAIL_VERIFIED_AT, record.getEmailVerifiedAt())
                .set(IDENTITY_USERS.UPDATED_AT, record.getUpdatedAt())
                .execute();
    }

    private static void validateRelatedRecordUserIds(UUID userId, UserPersistenceData data) {
        boolean containsForeignEmail = data.emails().stream()
                .anyMatch(record -> !userId.equals(record.getUserId()));
        if (containsForeignEmail) {
            throw new InvalidPersistenceDataException(
                    "Every email record must belong to the saved user"
            );
        }

        boolean containsForeignRole = data.roles().stream()
                .anyMatch(record -> !userId.equals(record.getUserId()));
        if (containsForeignRole) {
            throw new InvalidPersistenceDataException(
                    "Every role record must belong to the saved user"
            );
        }
    }

    private void synchronizeEmails(UUID userId, List<IdentityUserEmailsRecord> records) {
        Set<String> desiredKinds = records.stream()
                .map(IdentityUserEmailsRecord::getKind)
                .collect(Collectors.toUnmodifiableSet());

        Condition obsoleteEmail = IDENTITY_USER_EMAILS.USER_ID.eq(userId);
        if (!desiredKinds.isEmpty()) {
            obsoleteEmail = obsoleteEmail.and(IDENTITY_USER_EMAILS.KIND.notIn(desiredKinds));
        }
        dslContext.deleteFrom(IDENTITY_USER_EMAILS).where(obsoleteEmail).execute();

        for (IdentityUserEmailsRecord record : records) {
            dslContext
                    .insertInto(IDENTITY_USER_EMAILS)
                    .set(IDENTITY_USER_EMAILS.USER_ID, record.getUserId())
                    .set(IDENTITY_USER_EMAILS.EMAIL, record.getEmail())
                    .set(IDENTITY_USER_EMAILS.KIND, record.getKind())
                    .onConflict(IDENTITY_USER_EMAILS.USER_ID, IDENTITY_USER_EMAILS.KIND)
                    .doUpdate()
                    .set(IDENTITY_USER_EMAILS.EMAIL, record.getEmail())
                    .execute();
        }
    }

    private void synchronizeRoles(UUID userId, List<IdentityUserRolesRecord> records) {
        Set<String> desiredRoles = records.stream()
                .map(IdentityUserRolesRecord::getRole)
                .collect(Collectors.toUnmodifiableSet());

        Condition obsoleteRole = IDENTITY_USER_ROLES.USER_ID.eq(userId);
        if (!desiredRoles.isEmpty()) {
            obsoleteRole = obsoleteRole.and(IDENTITY_USER_ROLES.ROLE.notIn(desiredRoles));
        }
        dslContext.deleteFrom(IDENTITY_USER_ROLES).where(obsoleteRole).execute();

        for (IdentityUserRolesRecord record : records) {
            dslContext
                    .insertInto(IDENTITY_USER_ROLES)
                    .set(IDENTITY_USER_ROLES.USER_ID, record.getUserId())
                    .set(IDENTITY_USER_ROLES.ROLE, record.getRole())
                    .onConflict(IDENTITY_USER_ROLES.USER_ID, IDENTITY_USER_ROLES.ROLE)
                    .doNothing()
                    .execute();
        }
    }
}
