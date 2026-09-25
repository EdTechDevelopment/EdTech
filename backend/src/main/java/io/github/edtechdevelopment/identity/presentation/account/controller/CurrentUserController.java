package io.github.edtechdevelopment.identity.presentation.account.controller;

import io.github.edtechdevelopment.identity.application.port.in.account.UpdateCurrentUserUseCase;
import io.github.edtechdevelopment.identity.application.result.CurrentUserResult;
import io.github.edtechdevelopment.identity.presentation.account.mapper.UserPresentationMapper;
import io.github.edtechdevelopment.identity.presentation.account.model.request.UpdateCurrentUserRequest;
import io.github.edtechdevelopment.identity.presentation.account.model.response.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me")
public final class CurrentUserController {

    private final UpdateCurrentUserUseCase updateCurrentUserUseCase;
    private final UserPresentationMapper mapper;

    public CurrentUserController(
            UpdateCurrentUserUseCase updateCurrentUserUseCase,
            UserPresentationMapper mapper
    ) {
        this.updateCurrentUserUseCase = Objects.requireNonNull(
                updateCurrentUserUseCase,
                "Update current user use case must not be null"
        );
        this.mapper = Objects.requireNonNull(mapper, "User presentation mapper must not be null");
    }

    @PatchMapping
    public ResponseEntity<UserResponse> updateCurrentUser(
            Authentication authentication,
            @Valid @RequestBody UpdateCurrentUserRequest request
    ) {
        UUID userId = UUID.fromString(authentication.getName());
        CurrentUserResult result = updateCurrentUserUseCase.updateCurrentUser(
                mapper.toCommand(userId, request)
        );
        return ResponseEntity.ok(mapper.toResponse(result));
    }
}
