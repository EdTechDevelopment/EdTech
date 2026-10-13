package io.github.edtechdevelopment.workflows.application.registration.service;

import io.github.edtechdevelopment.identity.api.IdentityRegistrationGateway;
import io.github.edtechdevelopment.identity.api.command.registration.RegistrationData;
import io.github.edtechdevelopment.identity.api.command.registration.RegistrationReceipt;
import io.github.edtechdevelopment.identity.api.model.UserRoleView;
import io.github.edtechdevelopment.tutoring.api.command.profile.CreateInitialProfilesCommand;
import io.github.edtechdevelopment.tutoring.api.command.profile.TutoringProfileCreationCommands;
import io.github.edtechdevelopment.workflows.application.exception.WorkflowException;
import io.github.edtechdevelopment.workflows.application.idempotency.port.out.WorkflowFingerprint;
import io.github.edtechdevelopment.workflows.application.idempotency.port.out.WorkflowOperationRepository;
import io.github.edtechdevelopment.workflows.application.registration.model.RegistrationCommand;
import io.github.edtechdevelopment.workflows.application.registration.model.RegistrationOutcome;
import io.github.edtechdevelopment.workflows.application.registration.port.in.RegisterAccountUseCase;

import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class RegistrationWorkflow implements RegisterAccountUseCase {

    private final IdentityRegistrationGateway identity;
    private final TutoringProfileCreationCommands profiles;
    private final WorkflowOperationRepository operations;
    private final WorkflowFingerprint fingerprints;
    private final Clock clock;

    public RegistrationWorkflow(
            IdentityRegistrationGateway identity, TutoringProfileCreationCommands profiles,
            WorkflowOperationRepository operations, WorkflowFingerprint fingerprints, Clock clock) {
        this.identity = Objects.requireNonNull(identity, "Identity gateway must not be null");
        this.profiles = Objects.requireNonNull(profiles, "Profile commands must not be null");
        this.operations = Objects.requireNonNull(operations, "Operation repository must not be null");
        this.fingerprints = Objects.requireNonNull(fingerprints, "Fingerprint service must not be null");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
    }

    @Override
    @Transactional
    public RegistrationOutcome register(RegistrationCommand command) {
        Objects.requireNonNull(command, "Registration command must not be null");
        requireMatchingProfiles(command);
        String email = command.email().strip().toLowerCase(Locale.ROOT);
        String fingerprint = fingerprints.registration(command);
        Optional<RegistrationOutcome> replay = operations.beginRegistration(command.operationId(), email, fingerprint, clock.instant());
        if (replay.isPresent()) {
            return replay.get();
        }

        RegistrationReceipt account = identity.register(new RegistrationData(
                command.email(), command.rawPassword(), command.firstName(), command.lastName(), command.birthDate(), command.roles()));
        profiles.createInitialProfiles(new CreateInitialProfilesCommand(
                account.userId(), Optional.ofNullable(command.teacherProfile()), Optional.ofNullable(command.studentProfile())));
        RegistrationOutcome result = new RegistrationOutcome(account.email(), account.verificationExpiresAt());
        operations.completeRegistration(command.operationId(), email, fingerprint, result, clock.instant());
        return result;
    }

    private void requireMatchingProfiles(RegistrationCommand command) {
        Set<UserRoleView> roles = command.roles();
        if (roles == null || roles.isEmpty()) {
            throw new WorkflowException(WorkflowException.Reason.ROLE_PROFILE_MISMATCH, Set.of("roles"));
        }
        Set<String> invalid = new HashSet<>();
        if (roles.contains(UserRoleView.TEACHER) != (command.teacherProfile() != null)) {
            invalid.add("teacherProfile");
        }
        if (roles.contains(UserRoleView.STUDENT) != (command.studentProfile() != null)) {
            invalid.add("studentProfile");
        }
        if (!invalid.isEmpty()) {
            throw new WorkflowException(WorkflowException.Reason.ROLE_PROFILE_MISMATCH, invalid);
        }
    }
}

