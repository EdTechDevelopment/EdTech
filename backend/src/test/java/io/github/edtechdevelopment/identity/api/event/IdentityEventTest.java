package io.github.edtechdevelopment.identity.api.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdentityEventTest {

    private static final UUID EVENT_ID = UUID.fromString("bf8ce61c-ae81-4cac-bd11-dc86302eb7dd");
    private static final UUID USER_ID = UUID.fromString("25ca25ce-c1c7-4d4a-8cf6-ffb72618ad19");
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-14T10:00:00Z");

    @Test
    void storesRegisteredUserData() {
        UserRegisteredEvent event = new UserRegisteredEvent(
                EVENT_ID,
                USER_ID,
                "anna@example.com",
                OCCURRED_AT);

        assertEquals(EVENT_ID, event.eventId());
        assertEquals(USER_ID, event.userId());
        assertEquals("anna@example.com", event.email());
        assertEquals(OCCURRED_AT, event.occurredAt());
    }

    @Test
    void storesActivatedUserData() {
        UserActivatedEvent event = new UserActivatedEvent(
                EVENT_ID,
                USER_ID,
                "anna@example.com",
                OCCURRED_AT);

        assertEquals(EVENT_ID, event.eventId());
        assertEquals(USER_ID, event.userId());
        assertEquals("anna@example.com", event.email());
        assertEquals(OCCURRED_AT, event.occurredAt());
    }

    @Test
    void rejectsBlankEventEmail() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new UserRegisteredEvent(EVENT_ID, USER_ID, "  ", OCCURRED_AT));
        assertThrows(
                IllegalArgumentException.class,
                () -> new UserActivatedEvent(EVENT_ID, USER_ID, "  ", OCCURRED_AT));
    }

    @Test
    void protectsChangedFieldsFromExternalModification() {
        Set<String> sourceFields = new HashSet<>(Set.of("firstName"));
        UserAccountUpdatedEvent event = new UserAccountUpdatedEvent(
                EVENT_ID,
                USER_ID,
                sourceFields,
                OCCURRED_AT);

        sourceFields.add("lastName");

        assertEquals(Set.of("firstName"), event.changedFields());
        assertThrows(
                UnsupportedOperationException.class,
                () -> event.changedFields().add("email"));
    }

    @Test
    void rejectsEmptyChangedFields() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new UserAccountUpdatedEvent(EVENT_ID, USER_ID, Set.of(), OCCURRED_AT));
    }
}
