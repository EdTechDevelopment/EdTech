package io.github.edtechdevelopment;

import io.github.edtechdevelopment.identity.application.mapper.IdentityApiMapper;
import io.github.edtechdevelopment.identity.application.port.in.account.RegisterUserUseCase;
import io.github.edtechdevelopment.identity.application.port.out.messaging.VerificationEmailSender;
import io.github.edtechdevelopment.identity.application.service.account.RegisterUserService;
import io.github.edtechdevelopment.identity.infrastructure.messaging.email.NotificationVerificationEmailAdapter;
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
import org.springframework.transaction.IllegalTransactionStateException;

import java.net.URI;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ApplicationCompositionTest {

    private final RegisterUserUseCase registerUserUseCase;
    private final NotificationGateway notificationGateway;
    private final VerificationEmailSender verificationEmailSender;
    private final io.github.edtechdevelopment.notifications.application.port.out.email.VerificationEmailSender
            notificationDeliveryEmailSender;
    private final ProcessVerificationEmailDeliveriesService processingService;
    private final IdentityApiMapper identityApiMapper;

    @Autowired
    ApplicationCompositionTest(
            RegisterUserUseCase registerUserUseCase,
            NotificationGateway notificationGateway,
            VerificationEmailSender verificationEmailSender,
            io.github.edtechdevelopment.notifications.application.port.out.email.VerificationEmailSender
                    notificationDeliveryEmailSender,
            ProcessVerificationEmailDeliveriesService processingService,
            IdentityApiMapper identityApiMapper
    ) {
        this.registerUserUseCase = registerUserUseCase;
        this.notificationGateway = notificationGateway;
        this.verificationEmailSender = verificationEmailSender;
        this.notificationDeliveryEmailSender = notificationDeliveryEmailSender;
        this.processingService = processingService;
        this.identityApiMapper = identityApiMapper;
    }

    @Test
    void composesRegistrationPathAsSpringBeans() {
        assertAll(
                () -> assertTrue(AopUtils.isAopProxy(registerUserUseCase)),
                () -> assertEquals(RegisterUserService.class, AopUtils.getTargetClass(registerUserUseCase)),
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
                () -> assertEquals(IdentityApiMapper.class, identityApiMapper.getClass())
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
