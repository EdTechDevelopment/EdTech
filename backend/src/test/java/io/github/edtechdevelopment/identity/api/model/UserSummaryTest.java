package io.github.edtechdevelopment.identity.api.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserSummaryTest {

    private static final UUID USER_ID = UUID.fromString("c6a676a0-18c8-4449-87e6-998326fa7952");

    @Test
    void protectsRolesFromExternalModification() {
        Set<UserRoleView> sourceRoles = new HashSet<>(Set.of(UserRoleView.STUDENT));
        UserSummary summary = summary(sourceRoles);

        sourceRoles.add(UserRoleView.TEACHER);

        assertEquals(Set.of(UserRoleView.STUDENT), summary.roles());
        assertThrows(UnsupportedOperationException.class, () -> summary.roles().add(UserRoleView.TEACHER));
    }

    @Test
    void rejectsMissingRequiredData() {
        assertThrows(
                NullPointerException.class,
                () -> new UserSummary(
                        null,
                        "anna@example.com",
                        "Anna",
                        "Petrova",
                        Set.of(UserRoleView.STUDENT),
                        UserStatusView.ACTIVE
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> summary(Set.of())
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new UserSummary(
                        USER_ID,
                        "  ",
                        "Anna",
                        "Petrova",
                        Set.of(UserRoleView.STUDENT),
                        UserStatusView.ACTIVE
                )
        );
    }

    private static UserSummary summary(Set<UserRoleView> roles) {
        return new UserSummary(
                USER_ID,
                "anna@example.com",
                "Anna",
                "Petrova",
                roles,
                UserStatusView.ACTIVE
        );
    }
}
