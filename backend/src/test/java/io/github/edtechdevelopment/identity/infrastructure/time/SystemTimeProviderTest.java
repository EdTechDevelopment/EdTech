package io.github.edtechdevelopment.identity.infrastructure.time;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SystemTimeProviderTest {

    @Test
    void returnsTimeFromInjectedClockWithPostgresPrecision() {
        Instant clockTime = Instant.parse("2026-09-16T10:15:30.123456789Z");
        Clock fixedClock = Clock.fixed(clockTime, ZoneOffset.UTC);
        SystemTimeProvider timeProvider = new SystemTimeProvider(fixedClock);

        assertEquals(Instant.parse("2026-09-16T10:15:30.123456Z"), timeProvider.now());
    }
}
