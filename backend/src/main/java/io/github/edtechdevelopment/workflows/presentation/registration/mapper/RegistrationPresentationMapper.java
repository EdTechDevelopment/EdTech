package io.github.edtechdevelopment.workflows.presentation.registration.mapper;

import io.github.edtechdevelopment.workflows.application.registration.model.RegistrationCommand;
import io.github.edtechdevelopment.workflows.application.registration.model.RegistrationOutcome;
import io.github.edtechdevelopment.workflows.presentation.profile.ProfileInputPresentationMapper;
import io.github.edtechdevelopment.workflows.presentation.registration.model.RegistrationRequest;
import io.github.edtechdevelopment.workflows.presentation.registration.model.VerificationPendingResponse;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Component
public class RegistrationPresentationMapper {

    private final ProfileInputPresentationMapper profiles;

    public RegistrationPresentationMapper(ProfileInputPresentationMapper profiles) {
        this.profiles = profiles;
    }

    public RegistrationCommand toCommand(UUID operationId, RegistrationRequest request) {
        return new RegistrationCommand(operationId, request.email(), request.password(), request.firstName(),
                request.lastName(), request.birthDate(), Set.copyOf(request.roles()),
                request.teacherProfile() == null ? null : profiles.toData(request.teacherProfile()),
                request.studentProfile() == null ? null : profiles.toData(request.studentProfile()));
    }

    public VerificationPendingResponse toResponse(RegistrationOutcome result) {
        return new VerificationPendingResponse(result.email(), result.verificationExpiresAt());
    }
}

