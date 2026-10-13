package io.github.edtechdevelopment.workflows.application.registration.port.in;

import io.github.edtechdevelopment.workflows.application.registration.model.RegistrationCommand;
import io.github.edtechdevelopment.workflows.application.registration.model.RegistrationOutcome;

public interface RegisterAccountUseCase {

    RegistrationOutcome register(RegistrationCommand command);
}

