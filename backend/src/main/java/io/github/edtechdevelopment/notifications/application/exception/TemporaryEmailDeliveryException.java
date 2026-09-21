package io.github.edtechdevelopment.notifications.application.exception;

public class TemporaryEmailDeliveryException extends RuntimeException {

    public TemporaryEmailDeliveryException(String message) {
        super(message);
    }

    public TemporaryEmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
