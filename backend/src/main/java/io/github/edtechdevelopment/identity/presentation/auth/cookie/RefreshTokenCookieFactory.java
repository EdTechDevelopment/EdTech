package io.github.edtechdevelopment.identity.presentation.auth.cookie;

import io.github.edtechdevelopment.identity.application.model.IssuedRefreshToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Component
public final class RefreshTokenCookieFactory {

    public static final String COOKIE_NAME = "REFRESH_TOKEN";

    private static final String COOKIE_PATH = "/api/v1/auth";
    private static final String SAME_SITE_POLICY = "Lax";

    private final Clock clock;
    private final boolean secure;

    public RefreshTokenCookieFactory(
            Clock clock,
            @Value("${identity.security.refresh-cookie.secure}") boolean secure
    ) {
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
        this.secure = secure;
    }

    public ResponseCookie create(IssuedRefreshToken refreshToken) {
        Objects.requireNonNull(refreshToken, "Issued refresh token must not be null");

        Duration remainingLifetime = remainingLifetime(refreshToken.expiresAt(), clock.instant());
        return ResponseCookie.from(COOKIE_NAME, refreshToken.value())
                .httpOnly(true)
                .secure(secure)
                .sameSite(SAME_SITE_POLICY)
                .path(COOKIE_PATH)
                .maxAge(remainingLifetime)
                .build();
    }

    public ResponseCookie clear() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite(SAME_SITE_POLICY)
                .path(COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
    }

    private static Duration remainingLifetime(Instant expiresAt, Instant now) {
        Duration remaining = Duration.between(now, expiresAt);
        if (remaining.isZero() || remaining.isNegative()) {
            throw new IllegalArgumentException("Refresh token must not be expired when cookie is created");
        }

        long seconds = remaining.getSeconds();
        if (remaining.getNano() > 0) {
            seconds++;
        }
        return Duration.ofSeconds(seconds);
    }
}
