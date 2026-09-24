package io.github.edtechdevelopment.identity.presentation.account.mapper;

import io.github.edtechdevelopment.identity.api.model.UserRoleView;
import io.github.edtechdevelopment.identity.api.model.UserStatusView;
import io.github.edtechdevelopment.identity.application.command.account.UpdateCurrentUserCommand;
import io.github.edtechdevelopment.identity.application.result.CurrentUserResult;
import io.github.edtechdevelopment.identity.presentation.account.model.request.UpdateCurrentUserRequest;
import io.github.edtechdevelopment.identity.presentation.account.model.response.UserResponse;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public final class UserPresentationMapper {

    public UpdateCurrentUserCommand toCommand(UUID userId, UpdateCurrentUserRequest request) {
        Objects.requireNonNull(userId, "User id must not be null");
        Objects.requireNonNull(request, "Update current user request must not be null");

        return new UpdateCurrentUserCommand(
                userId,
                request.email(),
                request.firstName(),
                request.lastName()
        );
    }

    public UserResponse toResponse(CurrentUserResult result) {
        Objects.requireNonNull(result, "Current user result must not be null");

        return new UserResponse(
                result.id(),
                result.email(),
                result.pendingEmail(),
                result.firstName(),
                result.lastName(),
                result.roles().stream()
                        .map(role -> UserRoleView.valueOf(role.name()))
                        .collect(Collectors.toUnmodifiableSet()),
                UserStatusView.valueOf(result.status().name()),
                result.emailVerifiedAt(),
                result.createdAt(),
                result.updatedAt()
        );
    }
}
