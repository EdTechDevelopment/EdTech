package io.github.edtechdevelopment.identity.application.mapper;

import io.github.edtechdevelopment.identity.api.model.UserRoleView;
import io.github.edtechdevelopment.identity.api.model.UserStatusView;
import io.github.edtechdevelopment.identity.api.model.UserSummary;
import io.github.edtechdevelopment.identity.domain.user.model.Email;
import io.github.edtechdevelopment.identity.domain.user.model.PasswordHash;
import io.github.edtechdevelopment.identity.domain.user.model.User;
import io.github.edtechdevelopment.identity.domain.user.model.UserRole;
import io.github.edtechdevelopment.identity.domain.user.model.UserStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdentityApiMapperTest {

    private static final UUID USER_ID = UUID.fromString("f133f6fd-b989-420c-9457-5af69f19ffcf");
    private static final Instant CREATED_AT = Instant.parse("2026-09-24T08:00:00Z");

    private final IdentityApiMapper mapper = new IdentityApiMapper();

    @Test
    void mapsUserToSafePublicSummary() {
        User user = User.reconstitute(
                USER_ID,
                new Email("anna@example.com"),
                new Email("pending@example.com"),
                new PasswordHash("stored-password-hash"),
                "Anna",
                "Petrova",
                LocalDate.of(2000, 1, 1),
                Set.of(UserRole.TEACHER, UserRole.STUDENT),
                UserStatus.ACTIVE,
                CREATED_AT,
                CREATED_AT,
                CREATED_AT
        );

        UserSummary summary = mapper.toUserSummary(user);

        assertEquals(USER_ID, summary.id());
        assertEquals("anna@example.com", summary.email());
        assertEquals("Anna", summary.firstName());
        assertEquals("Petrova", summary.lastName());
        assertEquals(Set.of(UserRoleView.TEACHER, UserRoleView.STUDENT), summary.roles());
        assertEquals(UserStatusView.ACTIVE, summary.status());
    }
}
