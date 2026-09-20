package io.github.edtechdevelopment.identity.application.port.out.security;

import io.github.edtechdevelopment.identity.domain.verification.model.VerificationTokenHash;

public interface VerificationTokenHasher {

    VerificationTokenHash hash(String rawToken);
}
