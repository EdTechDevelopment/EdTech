package io.github.edtechdevelopment;

import io.github.edtechdevelopment.identity.application.mapper.IdentityApiMapper;
import io.github.edtechdevelopment.identity.api.IdentityRegistrationGateway;
import io.github.edtechdevelopment.identity.api.IdentityRoleGateway;
import io.github.edtechdevelopment.identity.api.query.IdentityQuery;
import io.github.edtechdevelopment.identity.application.port.in.account.RegisterUserUseCase;
import io.github.edtechdevelopment.identity.application.port.in.account.AddUserRoleUseCase;
import io.github.edtechdevelopment.identity.application.port.in.account.UpdateCurrentUserUseCase;
import io.github.edtechdevelopment.identity.application.port.in.authentication.LogoutUseCase;
import io.github.edtechdevelopment.identity.application.port.in.verification.ResendEmailVerificationUseCase;
import io.github.edtechdevelopment.identity.application.port.out.messaging.VerificationEmailSender;
import io.github.edtechdevelopment.identity.application.port.out.security.AccessTokenIssuer;
import io.github.edtechdevelopment.identity.application.service.account.RegisterUserService;
import io.github.edtechdevelopment.identity.application.service.account.AddUserRoleService;
import io.github.edtechdevelopment.identity.application.service.account.IdentityQueryService;
import io.github.edtechdevelopment.identity.application.service.account.UpdateCurrentUserService;
import io.github.edtechdevelopment.identity.application.service.authentication.LogoutService;
import io.github.edtechdevelopment.identity.application.service.verification.ResendEmailVerificationService;
import io.github.edtechdevelopment.identity.infrastructure.messaging.email.NotificationVerificationEmailAdapter;
import io.github.edtechdevelopment.identity.infrastructure.integration.RegistrationGatewayAdapter;
import io.github.edtechdevelopment.identity.infrastructure.integration.RoleGatewayAdapter;
import io.github.edtechdevelopment.identity.infrastructure.security.token.SpringJwtAccessTokenIssuer;
import io.github.edtechdevelopment.notifications.api.NotificationGateway;
import io.github.edtechdevelopment.notifications.api.command.SendVerificationEmailCommand;
import io.github.edtechdevelopment.notifications.api.model.VerificationEmailPurpose;
import io.github.edtechdevelopment.notifications.application.service.EnqueueVerificationEmailService;
import io.github.edtechdevelopment.notifications.application.service.ProcessVerificationEmailDeliveriesService;
import io.github.edtechdevelopment.notifications.infrastructure.messaging.email.SpringMailVerificationEmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.transaction.IllegalTransactionStateException;

import java.net.URI;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ApplicationCompositionTest {

    private final RegisterUserUseCase registerUserUseCase;
    private final AddUserRoleUseCase addUserRoleUseCase;
    private final IdentityRegistrationGateway identityRegistrationGateway;
    private final IdentityRoleGateway identityRoleGateway;
    private final IdentityQuery identityQuery;
    private final UpdateCurrentUserUseCase updateCurrentUserUseCase;
    private final LogoutUseCase logoutUseCase;
    private final ResendEmailVerificationUseCase resendEmailVerificationUseCase;
    private final NotificationGateway notificationGateway;
    private final VerificationEmailSender verificationEmailSender;
    private final io.github.edtechdevelopment.notifications.application.port.out.email.VerificationEmailSender
            notificationDeliveryEmailSender;
    private final ProcessVerificationEmailDeliveriesService processingService;
    private final IdentityApiMapper identityApiMapper;
    private final AccessTokenIssuer accessTokenIssuer;
    private final JwtDecoder jwtDecoder;

    @Autowired
    ApplicationCompositionTest(
            RegisterUserUseCase registerUserUseCase,
            AddUserRoleUseCase addUserRoleUseCase,
            IdentityRegistrationGateway identityRegistrationGateway,
            IdentityRoleGateway identityRoleGateway,
            IdentityQuery identityQuery,
            UpdateCurrentUserUseCase updateCurrentUserUseCase,
            LogoutUseCase logoutUseCase,
            ResendEmailVerificationUseCase resendEmailVerificationUseCase,
            NotificationGateway notificationGateway,
            VerificationEmailSender verificationEmailSender,
            io.github.edtechdevelopment.notifications.application.port.out.email.VerificationEmailSender
                    notificationDeliveryEmailSender,
            ProcessVerificationEmailDeliveriesService processingService,
            IdentityApiMapper identityApiMapper,
            AccessTokenIssuer accessTokenIssuer,
            JwtDecoder jwtDecoder
    ) {
        this.registerUserUseCase = registerUserUseCase;
        this.addUserRoleUseCase = addUserRoleUseCase;
        this.identityRegistrationGateway = identityRegistrationGateway;
        this.identityRoleGateway = identityRoleGateway;
        this.identityQuery = identityQuery;
        this.updateCurrentUserUseCase = updateCurrentUserUseCase;
        this.logoutUseCase = logoutUseCase;
        this.resendEmailVerificationUseCase = resendEmailVerificationUseCase;
        this.notificationGateway = notificationGateway;
        this.verificationEmailSender = verificationEmailSender;
        this.notificationDeliveryEmailSender = notificationDeliveryEmailSender;
        this.processingService = processingService;
        this.identityApiMapper = identityApiMapper;
        this.accessTokenIssuer = accessTokenIssuer;
        this.jwtDecoder = jwtDecoder;
    }

    @Test
    void composesRegistrationPathAsSpringBeans() {
        assertAll(
                () -> assertTrue(AopUtils.isAopProxy(registerUserUseCase)),
                () -> assertEquals(RegisterUserService.class, AopUtils.getTargetClass(registerUserUseCase)),
                () -> assertTrue(AopUtils.isAopProxy(addUserRoleUseCase)),
                () -> assertEquals(AddUserRoleService.class, AopUtils.getTargetClass(addUserRoleUseCase)),
                () -> assertEquals(RegistrationGatewayAdapter.class, identityRegistrationGateway.getClass()),
                () -> assertEquals(RoleGatewayAdapter.class, identityRoleGateway.getClass()),
                () -> assertTrue(AopUtils.isAopProxy(identityQuery)),
                () -> assertEquals(IdentityQueryService.class, AopUtils.getTargetClass(identityQuery)),
                () -> assertTrue(AopUtils.isAopProxy(updateCurrentUserUseCase)),
                () -> assertEquals(
                        UpdateCurrentUserService.class,
                        AopUtils.getTargetClass(updateCurrentUserUseCase)
                ),
                () -> assertTrue(AopUtils.isAopProxy(logoutUseCase)),
                () -> assertEquals(LogoutService.class, AopUtils.getTargetClass(logoutUseCase)),
                () -> assertTrue(AopUtils.isAopProxy(resendEmailVerificationUseCase)),
                () -> assertEquals(
                        ResendEmailVerificationService.class,
                        AopUtils.getTargetClass(resendEmailVerificationUseCase)
                ),
                () -> assertTrue(AopUtils.isAopProxy(notificationGateway)),
                () -> assertEquals(
                        EnqueueVerificationEmailService.class,
                        AopUtils.getTargetClass(notificationGateway)
                ),
                () -> assertEquals(
                        NotificationVerificationEmailAdapter.class,
                        verificationEmailSender.getClass()
                ),
                () -> assertEquals(
                        SpringMailVerificationEmailSender.class,
                        notificationDeliveryEmailSender.getClass()
                ),
                () -> assertEquals(
                        ProcessVerificationEmailDeliveriesService.class,
                        processingService.getClass()
                ),
                () -> assertEquals(IdentityApiMapper.class, identityApiMapper.getClass()),
                () -> assertEquals(SpringJwtAccessTokenIssuer.class, accessTokenIssuer.getClass()),
                () -> assertNotNull(jwtDecoder)
        );
    }

    @Test
    void notificationGatewayRequiresCallingTransaction() {
        SendVerificationEmailCommand command = new SendVerificationEmailCommand(
                "anna@example.com",
                URI.create("http://frontend.example:3000/verify-email?token=sensitive-token"),
                VerificationEmailPurpose.REGISTRATION,
                Instant.now().plusSeconds(300)
        );

        assertThrows(
                IllegalTransactionStateException.class,
                () -> notificationGateway.enqueue(command)
        );
    }
}
