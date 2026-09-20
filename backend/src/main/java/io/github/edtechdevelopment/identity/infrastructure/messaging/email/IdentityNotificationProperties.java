package io.github.edtechdevelopment.identity.infrastructure.messaging.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@ConfigurationProperties(prefix = "identity.notification")
public record IdentityNotificationProperties(
        URI frontendBaseUrl,
        String emailVerificationPath
) {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    public IdentityNotificationProperties {
        Objects.requireNonNull(frontendBaseUrl, "Frontend base URL must not be null");
        Objects.requireNonNull(emailVerificationPath, "Email verification path must not be null");

        validateFrontendBaseUrl(frontendBaseUrl);
        validateEmailVerificationPath(emailVerificationPath);
    }

    private static void validateFrontendBaseUrl(URI frontendBaseUrl) {
        String scheme = frontendBaseUrl.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Frontend base URL must use HTTP or HTTPS");
        }
        if (frontendBaseUrl.getHost() == null || frontendBaseUrl.getHost().isBlank()) {
            throw new IllegalArgumentException("Frontend base URL must contain a host");
        }
        if (frontendBaseUrl.getRawUserInfo() != null) {
            throw new IllegalArgumentException("Frontend base URL must not contain user information");
        }
        if (frontendBaseUrl.getRawQuery() != null || frontendBaseUrl.getRawFragment() != null) {
            throw new IllegalArgumentException("Frontend base URL must not contain query or fragment");
        }

        String path = frontendBaseUrl.getPath();
        if (path != null && !path.isEmpty() && !path.equals("/")) {
            throw new IllegalArgumentException("Frontend base URL must not contain a path");
        }
    }

    private static void validateEmailVerificationPath(String emailVerificationPath) {
        if (emailVerificationPath.isBlank()) {
            throw new IllegalArgumentException("Email verification path must not be blank");
        }
        if (!emailVerificationPath.startsWith("/")) {
            throw new IllegalArgumentException("Email verification path must start with '/'");
        }
        if (emailVerificationPath.contains("?") || emailVerificationPath.contains("#")) {
            throw new IllegalArgumentException("Email verification path must not contain query or fragment");
        }
    }
}
