package io.github.edtechdevelopment.tutoring.application.integration.identity.port.in;

import io.github.edtechdevelopment.tutoring.application.integration.identity.model.command.HandleAccountEmailVerifiedCommand;

public interface HandleAccountEmailVerifiedUseCase {

    void handle(HandleAccountEmailVerifiedCommand command);
}
