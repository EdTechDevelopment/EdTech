package io.github.edtechdevelopment.notifications.application.service;

import io.github.edtechdevelopment.notifications.api.InvitationNotificationGateway;
import io.github.edtechdevelopment.notifications.api.command.SendStudentInvitationCommand;
import io.github.edtechdevelopment.notifications.application.port.out.email.InvitationLinkFactory;
import io.github.edtechdevelopment.notifications.application.port.out.persistence.InvitationEmailDeliveryRepository;
import io.github.edtechdevelopment.notifications.domain.delivery.model.InvitationEmailDelivery;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

public class EnqueueStudentInvitationService implements InvitationNotificationGateway {
    private final InvitationEmailDeliveryRepository repository;
    private final InvitationLinkFactory links;
    private final Clock clock;
    public EnqueueStudentInvitationService(InvitationEmailDeliveryRepository repository, InvitationLinkFactory links, Clock clock) {
        this.repository = repository;
        this.links = links;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(SendStudentInvitationCommand command) {
        repository.enqueue(new InvitationEmailDelivery(UUID.randomUUID(), command.invitationId(), command.recipientEmail(),
                command.teacherDisplayName(), links.create(command.invitationId(), command.recipientEmail()),
                command.expiresAt(), clock.instant(), command.deduplicationKey()));
    }
}
