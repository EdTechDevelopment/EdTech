package io.github.edtechdevelopment.workflows.infrastructure.configuration;

import io.github.edtechdevelopment.identity.api.IdentityRegistrationGateway;
import io.github.edtechdevelopment.identity.api.IdentityRoleGateway;
import io.github.edtechdevelopment.identity.api.query.IdentityQuery;
import io.github.edtechdevelopment.identity.api.query.OwnAccountQuery;
import io.github.edtechdevelopment.tutoring.api.command.profile.TutoringProfileCreationCommands;
import io.github.edtechdevelopment.tutoring.api.query.TutoringProfileQuery;
import io.github.edtechdevelopment.workflows.application.idempotency.port.out.WorkflowFingerprint;
import io.github.edtechdevelopment.workflows.application.idempotency.port.out.WorkflowOperationRepository;
import io.github.edtechdevelopment.workflows.application.me.port.in.GetMeUseCase;
import io.github.edtechdevelopment.workflows.application.me.service.MeQueryFacade;
import io.github.edtechdevelopment.workflows.application.onboarding.service.RoleOnboardingWorkflow;
import io.github.edtechdevelopment.workflows.application.registration.service.RegistrationWorkflow;
import io.github.edtechdevelopment.workflows.infrastructure.idempotency.WorkflowFingerprintProperties;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WorkflowFingerprintProperties.class)
public class WorkflowsConfiguration {

    @Bean
    RegistrationWorkflow registrationWorkflow(
            IdentityRegistrationGateway identity, TutoringProfileCreationCommands profiles,
            WorkflowOperationRepository operations, WorkflowFingerprint fingerprint, Clock clock) {
        return new RegistrationWorkflow(identity, profiles, operations, fingerprint, clock);
    }

    @Bean
    MeQueryFacade meQueryFacade(OwnAccountQuery accounts, TutoringProfileQuery profiles) {
        return new MeQueryFacade(accounts, profiles);
    }

    @Bean
    RoleOnboardingWorkflow roleOnboardingWorkflow(
            IdentityQuery accounts, IdentityRoleGateway identity, TutoringProfileCreationCommands profiles,
            GetMeUseCase me, WorkflowOperationRepository operations, WorkflowFingerprint fingerprint, Clock clock) {
        return new RoleOnboardingWorkflow(accounts, identity, profiles, me, operations, fingerprint, clock);
    }
}

