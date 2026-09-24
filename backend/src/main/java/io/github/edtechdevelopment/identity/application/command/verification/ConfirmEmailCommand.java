package io.github.edtechdevelopment.identity.application.command.verification;

public record ConfirmEmailCommand(String rawToken) {

    @Override
    public String toString() {
        return "ConfirmEmailCommand[PROTECTED]";
    }
}
