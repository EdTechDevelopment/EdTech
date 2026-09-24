package io.github.edtechdevelopment.identity.application.exception;

public class AccountOperationNotAllowedException extends RuntimeException {

    public AccountOperationNotAllowedException() {
        super("Account access is not allowed");
    }
}
