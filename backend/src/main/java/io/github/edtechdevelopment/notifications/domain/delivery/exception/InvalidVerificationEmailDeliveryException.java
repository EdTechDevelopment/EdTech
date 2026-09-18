package io.github.edtechdevelopment.notifications.domain.delivery.exception;

public class InvalidVerificationEmailDeliveryException extends RuntimeException {

    public InvalidVerificationEmailDeliveryException(String message) {
        super(message);
    }
}
