package io.github.edtechdevelopment.identity.application.command.authentication;

public record LoginCommand(String email, String rawPassword) {

    @Override
    public String toString() {
        return "LoginCommand[PROTECTED]";
    }
}
