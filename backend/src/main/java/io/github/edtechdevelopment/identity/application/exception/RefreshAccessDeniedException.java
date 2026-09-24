package io.github.edtechdevelopment.identity.application.exception;

public class RefreshAccessDeniedException extends RuntimeException {

    public RefreshAccessDeniedException() {
        super("Account access is not allowed");
    }
}
