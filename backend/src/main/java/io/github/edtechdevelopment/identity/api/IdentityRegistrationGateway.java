package io.github.edtechdevelopment.identity.api;

import io.github.edtechdevelopment.identity.api.command.registration.RegistrationData;
import io.github.edtechdevelopment.identity.api.command.registration.RegistrationReceipt;

public interface IdentityRegistrationGateway {

    RegistrationReceipt register(RegistrationData data);
}
