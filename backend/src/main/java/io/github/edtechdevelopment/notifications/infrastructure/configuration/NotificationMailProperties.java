package io.github.edtechdevelopment.notifications.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Objects;
import java.util.regex.Pattern;

@ConfigurationProperties(prefix = "notifications.mail")
public record NotificationMailProperties(String fromAddress) {

    private static final int MAX_EMAIL_LENGTH = 254;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+$");

    public NotificationMailProperties {
        Objects.requireNonNull(fromAddress, "Notification sender address must not be null");
        fromAddress = fromAddress.strip();
        if (fromAddress.isEmpty()) {
            throw new IllegalArgumentException("Notification sender address must not be blank");
        }
        if (fromAddress.length() > MAX_EMAIL_LENGTH || !EMAIL_PATTERN.matcher(fromAddress).matches()) {
            throw new IllegalArgumentException("Notification sender address has invalid format");
        }
    }
}
