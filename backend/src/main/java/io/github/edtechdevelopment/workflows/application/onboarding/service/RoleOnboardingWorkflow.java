package io.github.edtechdevelopment.workflows.application.onboarding.service;

import io.github.edtechdevelopment.identity.api.IdentityRoleGateway;
import io.github.edtechdevelopment.identity.api.command.role.AddUserRoleCommand;
import io.github.edtechdevelopment.identity.api.model.UserRoleView;
import io.github.edtechdevelopment.identity.api.model.UserStatusView;
import io.github.edtechdevelopment.identity.api.model.UserSummary;
import io.github.edtechdevelopment.identity.api.query.IdentityQuery;
import io.github.edtechdevelopment.tutoring.api.command.profile.CreateStudentProfileCommand;
import io.github.edtechdevelopment.tutoring.api.command.profile.CreateTeacherProfileCommand;
import io.github.edtechdevelopment.tutoring.api.command.profile.TutoringProfileCreationCommands;
import io.github.edtechdevelopment.workflows.application.exception.WorkflowException;
import io.github.edtechdevelopment.workflows.application.idempotency.model.WorkflowOperationType;
import io.github.edtechdevelopment.workflows.application.idempotency.port.out.WorkflowFingerprint;
import io.github.edtechdevelopment.workflows.application.idempotency.port.out.WorkflowOperationRepository;
import io.github.edtechdevelopment.workflows.application.me.model.MeResult;
import io.github.edtechdevelopment.workflows.application.me.port.in.GetMeUseCase;
import io.github.edtechdevelopment.workflows.application.onboarding.model.AddStudentRoleCommand;
import io.github.edtechdevelopment.workflows.application.onboarding.model.AddTeacherRoleCommand;
import io.github.edtechdevelopment.workflows.application.onboarding.port.in.AddStudentRoleUseCase;
import io.github.edtechdevelopment.workflows.application.onboarding.port.in.AddTeacherRoleUseCase;

import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class RoleOnboardingWorkflow implements AddTeacherRoleUseCase, AddStudentRoleUseCase {

    private final IdentityQuery accounts;
    private final IdentityRoleGateway identity;
    private final TutoringProfileCreationCommands profiles;
    private final GetMeUseCase me;
    private final WorkflowOperationRepository operations;
    private final WorkflowFingerprint fingerprints;
    private final Clock clock;

    public RoleOnboardingWorkflow(
            IdentityQuery accounts, IdentityRoleGateway identity, TutoringProfileCreationCommands profiles,
            GetMeUseCase me, WorkflowOperationRepository operations, WorkflowFingerprint fingerprints, Clock clock) {
        this.accounts = Objects.requireNonNull(accounts, "Account query must not be null");
        this.identity = Objects.requireNonNull(identity, "Role gateway must not be null");
        this.profiles = Objects.requireNonNull(profiles, "Profile commands must not be null");
        this.me = Objects.requireNonNull(me, "Me query must not be null");
        this.operations = Objects.requireNonNull(operations, "Operation repository must not be null");
        this.fingerprints = Objects.requireNonNull(fingerprints, "Fingerprint service must not be null");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
    }

    @Override
    @Transactional
    public MeResult addTeacherRole(AddTeacherRoleCommand command) {
        Objects.requireNonNull(command, "Teacher onboarding command must not be null");
        UserSummary account = requireActiveAccount(command.actorUserId());
        String fingerprint = fingerprints.teacherOnboarding(command);
        Optional<MeResult> replay = operations.beginOnboarding(
                command.operationId(), command.actorUserId(), WorkflowOperationType.ADD_TEACHER_ROLE, fingerprint, clock.instant());
        if (replay.isPresent()) {
            return replay.get();
        }
        requireNewRole(account, UserRoleView.TEACHER);
        identity.addRole(new AddUserRoleCommand(command.actorUserId(), UserRoleView.TEACHER));
        profiles.createTeacherProfile(new CreateTeacherProfileCommand(command.actorUserId(), command.profile()));
        MeResult result = me.getMe(command.actorUserId());
        operations.completeOnboarding(command.operationId(), command.actorUserId(),
                WorkflowOperationType.ADD_TEACHER_ROLE, fingerprint, result, clock.instant());
        return result;
    }

    @Override
    @Transactional
    public MeResult addStudentRole(AddStudentRoleCommand command) {
        Objects.requireNonNull(command, "Student onboarding command must not be null");
        UserSummary account = requireActiveAccount(command.actorUserId());
        String fingerprint = fingerprints.studentOnboarding(command);
        Optional<MeResult> replay = operations.beginOnboarding(
                command.operationId(), command.actorUserId(), WorkflowOperationType.ADD_STUDENT_ROLE, fingerprint, clock.instant());
        if (replay.isPresent()) {
            return replay.get();
        }
        requireNewRole(account, UserRoleView.STUDENT);
        identity.addRole(new AddUserRoleCommand(command.actorUserId(), UserRoleView.STUDENT));
        profiles.createStudentProfile(new CreateStudentProfileCommand(command.actorUserId(), command.profile()));
        MeResult result = me.getMe(command.actorUserId());
        operations.completeOnboarding(command.operationId(), command.actorUserId(),
                WorkflowOperationType.ADD_STUDENT_ROLE, fingerprint, result, clock.instant());
        return result;
    }

    private UserSummary requireActiveAccount(UUID userId) {
        UserSummary account = accounts.findUserById(userId)
                .orElseThrow(() -> new WorkflowException(WorkflowException.Reason.ACCOUNT_UNAVAILABLE));
        if (account.status() != UserStatusView.ACTIVE) {
            throw new WorkflowException(WorkflowException.Reason.ACCOUNT_NOT_ACTIVE);
        }
        return account;
    }

    private void requireNewRole(UserSummary account, UserRoleView role) {
        if (account.roles().contains(role)) {
            throw new WorkflowException(WorkflowException.Reason.ROLE_ALREADY_ENABLED);
        }
    }
}

