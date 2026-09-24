package io.github.edtechdevelopment.identity.application.mapper;

import io.github.edtechdevelopment.identity.application.result.CurrentUserResult;
import io.github.edtechdevelopment.identity.domain.user.model.User;

import java.util.Objects;

public final class UserResultMapper {

    public CurrentUserResult toCurrentUserResult(User user) {
        Objects.requireNonNull(user, "User must not be null");

        return new CurrentUserResult(
                user.id(),
                user.email().value(),
                user.pendingEmail().map(email -> email.value()).orElse(null),
                user.firstName(),
                user.lastName(),
                user.roles(),
                user.status(),
                user.emailVerifiedAt().orElse(null),
                user.createdAt(),
                user.updatedAt()
        );
    }
}
