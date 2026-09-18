package io.github.edtechdevelopment.identity.infrastructure.security.token;

import io.github.edtechdevelopment.identity.application.port.out.security.VerificationTokenGenerator;
import io.github.edtechdevelopment.identity.infrastructure.security.configuration.IdentityTokenProperties;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

@Component
public final class SecureVerificationTokenGenerator implements VerificationTokenGenerator {

    private final SecureRandom secureRandom;
    private final int entropyBytes;

    public SecureVerificationTokenGenerator(IdentityTokenProperties properties) {
        Objects.requireNonNull(properties, "Identity token properties must not be null");
        secureRandom = new SecureRandom();
        entropyBytes = properties.verificationEntropyBytes();
    }

    @Override
    public String generate() {
        byte[] randomBytes = new byte[entropyBytes];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
