package io.github.edtechdevelopment.notifications.application.service;

import io.github.edtechdevelopment.notifications.application.exception.*;
import io.github.edtechdevelopment.notifications.application.model.ClaimedInvitationEmail;
import io.github.edtechdevelopment.notifications.application.port.out.email.InvitationEmailSender;
import io.github.edtechdevelopment.notifications.application.port.out.persistence.InvitationEmailDeliveryRepository;
import io.github.edtechdevelopment.notifications.domain.delivery.model.*;
import io.github.edtechdevelopment.notifications.infrastructure.configuration.NotificationMailProperties;
import io.github.edtechdevelopment.notifications.infrastructure.messaging.email.SpringMailInvitationEmailSender;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.support.TransactionOperations;
import java.net.URI;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProcessInvitationEmailDeliveriesServiceTest {

    private final Instant now = Instant.parse("2026-10-13T10:00:00Z");
    private final InvitationEmailDeliveryRepository repository = mock(InvitationEmailDeliveryRepository.class);
    private final InvitationEmailSender sender = mock(InvitationEmailSender.class);
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final UUID claimId = UUID.randomUUID();

    @Test
    void sendsAndAcknowledgesSuccessfulDelivery() {
        InvitationEmailDelivery delivery = prepare(now.plusSeconds(60));
        service().processPending();
        verify(sender).send(delivery);
        verify(repository).complete(delivery.id(), claimId, DeliveryStatus.SENT, now);
    }

    @Test
    void retriesTemporarySmtpFailure() {
        InvitationEmailDelivery delivery = prepare(now.plusSeconds(60));
        doThrow(new TemporaryEmailDeliveryException("Unavailable")).when(sender).send(delivery);
        service().processPending();
        verify(repository).complete(delivery.id(), claimId, DeliveryStatus.PENDING, now);
    }

    @Test
    void stopsRetryingPermanentFailure() {
        InvitationEmailDelivery delivery = prepare(now.plusSeconds(60));
        doThrow(new PermanentEmailDeliveryException("Invalid message")).when(sender).send(delivery);
        service().processPending();
        verify(repository).complete(delivery.id(), claimId, DeliveryStatus.FAILED, now);
    }

    @Test
    void doesNotSendAtExactExpirationBoundary() {
        InvitationEmailDelivery delivery = prepare(now);
        service().processPending();
        verifyNoInteractions(sender);
        verify(repository).complete(delivery.id(), claimId, DeliveryStatus.EXPIRED, now);
    }

    @Test
    void buildsInvitationLetterContainingLinkAndAnExplicitAcceptanceInstruction() {
        InvitationEmailDelivery delivery = prepare(now.plusSeconds(60));
        JavaMailSender mail = mock(JavaMailSender.class);
        SpringMailInvitationEmailSender smtp = new SpringMailInvitationEmailSender(mail, new NotificationMailProperties("no-reply@example.test"));
        smtp.send(delivery);
        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(message.capture());
        assertThat(message.getValue().getTo()).containsExactly(delivery.recipientEmail());
        assertThat(message.getValue().getText()).contains(delivery.invitationUrl().toString(), "Teacher", "принять отдельно");
    }

    private ProcessInvitationEmailDeliveriesService service() {
        return new ProcessInvitationEmailDeliveriesService(repository, sender, TransactionOperations.withoutTransaction(), clock, 10, Duration.ofMinutes(1));
    }

    private InvitationEmailDelivery prepare(Instant expiresAt) {
        InvitationEmailDelivery delivery = new InvitationEmailDelivery(UUID.randomUUID(), UUID.randomUUID(), "student@example.test", "Teacher",
                URI.create("https://frontend.example/invitations/test?email=student%40example.test"), expiresAt, now.minusSeconds(60), "test-key");
        when(repository.claimPending(now, now.minusSeconds(60), 10)).thenReturn(List.of(new ClaimedInvitationEmail(delivery, claimId)));
        return delivery;
    }
}

