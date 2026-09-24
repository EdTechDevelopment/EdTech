package io.github.edtechdevelopment.identity.application.command.authentication;

public record RefreshTokenCommand(String rawRefreshToken) {

    @Override
    public String toString() {
        return "RefreshTokenCommand[PROTECTED]";
    }
}
