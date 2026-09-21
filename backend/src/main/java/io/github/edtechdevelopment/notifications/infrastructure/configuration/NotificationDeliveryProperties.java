package io.github.edtechdevelopment.notifications.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties(prefix = "notifications.delivery")
public record NotificationDeliveryProperties(
        int batchSize,
        Duration processingTimeout,
        Duration pollDelay,
        Duration initialDelay,
        boolean schedulerEnabled
) {

    public NotificationDeliveryProperties {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Notification delivery batch size must be positive");
        }
        Objects.requireNonNull(processingTimeout, "Notification delivery processing timeout must not be null");
        if (processingTimeout.isZero() || processingTimeout.isNegative()) {
            throw new IllegalArgumentException("Notification delivery processing timeout must be positive");
        }
        requirePositiveDuration(pollDelay, "Notification delivery poll delay");
        Objects.requireNonNull(initialDelay, "Notification delivery initial delay must not be null");
        if (initialDelay.isNegative()) {
            throw new IllegalArgumentException("Notification delivery initial delay must not be negative");
        }
    }

    private static void requirePositiveDuration(Duration duration, String propertyName) {
        Objects.requireNonNull(duration, propertyName + " must not be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(propertyName + " must be positive");
        }
    }
}
