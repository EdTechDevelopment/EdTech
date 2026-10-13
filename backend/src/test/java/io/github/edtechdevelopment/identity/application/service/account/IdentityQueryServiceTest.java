package io.github.edtechdevelopment.identity.application.service.account;

import io.github.edtechdevelopment.identity.api.model.UserRoleView;
import io.github.edtechdevelopment.identity.api.model.UserStatusView;
import io.github.edtechdevelopment.identity.api.model.UserSummary;
import io.github.edtechdevelopment.identity.application.mapper.IdentityApiMapper;
import io.github.edtechdevelopment.identity.application.port.out.persistence.UserRepository;
import io.github.edtechdevelopment.identity.domain.user.model.Email;
import io.github.edtechdevelopment.identity.domain.user.model.PasswordHash;
import io.github.edtechdevelopment.identity.domain.user.model.User;
import io.github.edtechdevelopment.identity.domain.user.model.UserRole;
import io.github.edtechdevelopment.identity.domain.user.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IdentityQueryServiceTest {

    private static final UUID USER_ID = UUID.fromString("81855523-ddfe-4977-a09e-0b5eff18328f");
    private static final Instant CREATED_AT = Instant.parse("2026-09-24T08:00:00Z");

    private UserRepository userRepository;
    private IdentityQueryService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        service = new IdentityQueryService(userRepository, new IdentityApiMapper());
    }

    @Test
    void returnsSafeSummaryWhenUserExists() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));

        UserSummary summary = service.findUserById(USER_ID).orElseThrow();

        assertEquals(USER_ID, summary.id());
        assertEquals("anna@example.com", summary.email());
        assertEquals(Set.of(UserRoleView.STUDENT), summary.roles());
        assertEquals(UserStatusView.ACTIVE, summary.status());
    }

    @Test
    void returnsEmptyWhenUserDoesNotExist() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertTrue(service.findUserById(USER_ID).isEmpty());
    }

    @Test
    void rejectsMissingUserIdBeforeRepositoryLookup() {
        assertThrows(NullPointerException.class, () -> service.findUserById(null));
        verifyNoInteractions(userRepository);
    }

    @Test
    void calculatesAgeOnBothSidesOfBirthday() {
        when(userRepository.findByIds(Set.of(USER_ID))).thenReturn(Map.of(USER_ID, activeUser()));

        assertEquals(25, service.findAgesByIds(Set.of(USER_ID), LocalDate.of(2025, 12, 31)).get(USER_ID));
        assertEquals(26, service.findAgesByIds(Set.of(USER_ID), LocalDate.of(2026, 1, 1)).get(USER_ID));
    }

    @Test
    void skipsRepositoryForEmptyAgeBatch() {
        assertTrue(service.findAgesByIds(Set.of(), LocalDate.of(2026, 1, 1)).isEmpty());
        verifyNoInteractions(userRepository);
    }

    private static User activeUser() {
        return User.reconstitute(
                USER_ID,
                new Email("anna@example.com"),
                null,
                new PasswordHash("stored-password-hash"),
                "Anna",
                "Petrova",
                LocalDate.of(2000, 1, 1),
                Set.of(UserRole.STUDENT),
                UserStatus.ACTIVE,
                CREATED_AT,
                CREATED_AT,
                CREATED_AT
        );
    }
}
