package io.github.edtechdevelopment.identity.api.exception;

public class IdentityCommandException extends RuntimeException {

    public enum Reason {
        INVALID_DATA,
        EMAIL_ALREADY_EXISTS,
        ACCOUNT_STATE_CONFLICT
    }

    private final Reason reason;

    public IdentityCommandException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}

