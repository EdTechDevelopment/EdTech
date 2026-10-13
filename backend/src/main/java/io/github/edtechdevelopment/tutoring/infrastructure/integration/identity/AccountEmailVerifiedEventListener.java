package io.github.edtechdevelopment.tutoring.infrastructure.integration.identity;

import io.github.edtechdevelopment.identity.api.event.AccountEmailVerifiedEvent;
import io.github.edtechdevelopment.tutoring.application.integration.identity.model.command.HandleAccountEmailVerifiedCommand;
import io.github.edtechdevelopment.tutoring.application.integration.identity.port.in.HandleAccountEmailVerifiedUseCase;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AccountEmailVerifiedEventListener {
    private final HandleAccountEmailVerifiedUseCase useCase;
    public AccountEmailVerifiedEventListener(HandleAccountEmailVerifiedUseCase useCase) {
        this.useCase = useCase;
    }

    @EventListener
    public void on(AccountEmailVerifiedEvent event) {
        useCase.handle(new HandleAccountEmailVerifiedCommand(event.eventId(), event.userId(), event.email(), event.occurredAt()));
    }
}
