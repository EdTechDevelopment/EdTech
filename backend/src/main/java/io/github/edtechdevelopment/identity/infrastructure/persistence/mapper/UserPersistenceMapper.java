package io.github.edtechdevelopment.identity.infrastructure.persistence.mapper;

import io.github.edtechdevelopment.identity.domain.user.exception.InvalidEmailException;
import io.github.edtechdevelopment.identity.domain.user.exception.InvalidUserDataException;
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
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component
public final class UserPersistenceMapper {

    private static final String CURRENT_EMAIL_KIND = "CURRENT";
    private static final String PENDING_EMAIL_KIND = "PENDING";

    public User toDomain(UserPersistenceData data) {
        Objects.requireNonNull(data, "User persistence data must not be null");

        IdentityUsersRecord userRecord = data.user();
        UUID userId = requireValue(userRecord.getId(), "User id must not be null");
        MappedEmails mappedEmails = mapEmails(userId, data.emails());
        Set<UserRole> roles = mapRoles(userId, data.roles());

        try {
            return User.reconstitute(
                    userId,
                    new Email(mappedEmails.current()),
                    toEmail(mappedEmails.pending()),
                    new PasswordHash(userRecord.getPasswordHash()),
                    userRecord.getFirstName(),
                    userRecord.getLastName(),
                    roles,
                    toUserStatus(userRecord.getStatus()),
                    toInstant(userRecord.getEmailVerifiedAt()),
                    toRequiredInstant(userRecord.getCreatedAt(), "User creation time must not be null"),
                    toRequiredInstant(userRecord.getUpdatedAt(), "User update time must not be null")
            );
        } catch (InvalidEmailException | InvalidUserDataException exception) {
            throw new InvalidPersistenceDataException("Cannot reconstitute user from persistence data", exception);
        }
    }

    public UserPersistenceData toPersistence(User user) {
        Objects.requireNonNull(user, "User must not be null");

        IdentityUsersRecord userRecord = new IdentityUsersRecord(
                user.id(),
                user.passwordHash().value(),
                user.firstName(),
                user.lastName(),
                user.status().name(),
                toOffsetDateTime(user.emailVerifiedAt().orElse(null)),
                toOffsetDateTime(user.createdAt()),
                toOffsetDateTime(user.updatedAt())
        );

        List<IdentityUserEmailsRecord> emailRecords = new ArrayList<>();
        emailRecords.add(new IdentityUserEmailsRecord(
                user.id(),
                user.email().value(),
                CURRENT_EMAIL_KIND
        ));
        user.pendingEmail().ifPresent(pendingEmail -> emailRecords.add(new IdentityUserEmailsRecord(
                user.id(),
                pendingEmail.value(),
                PENDING_EMAIL_KIND
        )));

        List<IdentityUserRolesRecord> roleRecords = user.roles().stream()
                .sorted(Comparator.comparing(UserRole::name))
                .map(role -> new IdentityUserRolesRecord(user.id(), role.name()))
                .toList();

        return new UserPersistenceData(userRecord, emailRecords, roleRecords);
    }

    private static MappedEmails mapEmails(UUID userId, List<IdentityUserEmailsRecord> records) {
        String currentEmail = null;
        String pendingEmail = null;

        for (IdentityUserEmailsRecord record : records) {
            requireMatchingUserId(userId, record.getUserId(), "email");
            String kind = requireValue(record.getKind(), "Email kind must not be null");
            String email = requireValue(record.getEmail(), "Email value must not be null");

            switch (kind) {
                case CURRENT_EMAIL_KIND -> {
                    if (currentEmail != null) {
                        throw new InvalidPersistenceDataException("User must have exactly one current email");
                    }
                    currentEmail = email;
                }
                case PENDING_EMAIL_KIND -> {
                    if (pendingEmail != null) {
                        throw new InvalidPersistenceDataException("User must not have more than one pending email");
                    }
                    pendingEmail = email;
                }
                default -> throw new InvalidPersistenceDataException("Unknown email kind: " + kind);
            }
        }

        if (currentEmail == null) {
            throw new InvalidPersistenceDataException("User must have exactly one current email");
        }
        return new MappedEmails(currentEmail, pendingEmail);
    }

    private static Set<UserRole> mapRoles(UUID userId, List<IdentityUserRolesRecord> records) {
        if (records.isEmpty()) {
            throw new InvalidPersistenceDataException("User must have at least one role");
        }

        Set<UserRole> roles = EnumSet.noneOf(UserRole.class);
        for (IdentityUserRolesRecord record : records) {
            requireMatchingUserId(userId, record.getUserId(), "role");
            String role = requireValue(record.getRole(), "User role must not be null");

            try {
                roles.add(UserRole.valueOf(role));
            } catch (IllegalArgumentException exception) {
                throw new InvalidPersistenceDataException("Unknown user role: " + role, exception);
            }
        }
        return roles;
    }

    private static UserStatus toUserStatus(String value) {
        requireValue(value, "User status must not be null");
        try {
            return UserStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new InvalidPersistenceDataException("Unknown user status: " + value, exception);
        }
    }

    private static Email toEmail(String value) {
        return value == null ? null : new Email(value);
    }

    private static Instant toRequiredInstant(OffsetDateTime value, String message) {
        return requireValue(value, message).toInstant();
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime toOffsetDateTime(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static void requireMatchingUserId(UUID expectedUserId, UUID actualUserId, String recordType) {
        if (!expectedUserId.equals(actualUserId)) {
            throw new InvalidPersistenceDataException(
                    "User id in " + recordType + " record must match the main user record"
            );
        }
    }

    private static <T> T requireValue(T value, String message) {
        if (value == null) {
            throw new InvalidPersistenceDataException(message);
        }
        return value;
    }

    private record MappedEmails(String current, String pending) {
    }
}
