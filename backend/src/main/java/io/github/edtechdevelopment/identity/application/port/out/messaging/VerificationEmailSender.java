package io.github.edtechdevelopment.identity.application.port.out.messaging;

import io.github.edtechdevelopment.identity.domain.user.model.Email;
import io.github.edtechdevelopment.identity.domain.verification.model.VerificationPurpose;

import java.time.Instant;

public interface VerificationEmailSender {

    void sendVerificationEmail(
            Email recipient,
            String rawToken,
            VerificationPurpose purpose,
            Instant expiresAt
    );
}
