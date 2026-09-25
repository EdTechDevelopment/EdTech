package io.github.edtechdevelopment.identity.presentation.auth;

import io.github.edtechdevelopment.identity.presentation.auth.cookie.RefreshTokenCookieFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshTokenCookieFactoryTest {

    @Test
    void clearsRefreshCookieWithTheSameSecurityAttributes() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-24T12:00:00Z"), ZoneOffset.UTC);
        RefreshTokenCookieFactory factory = new RefreshTokenCookieFactory(clock, false);

        ResponseCookie cookie = factory.clear();

        assertEquals(RefreshTokenCookieFactory.COOKIE_NAME, cookie.getName());
        assertEquals("", cookie.getValue());
        assertEquals("/api/v1/auth", cookie.getPath());
        assertEquals(0, cookie.getMaxAge().getSeconds());
        assertEquals("Lax", cookie.getSameSite());
        assertTrue(cookie.isHttpOnly());
        assertFalse(cookie.isSecure());
    }
}
