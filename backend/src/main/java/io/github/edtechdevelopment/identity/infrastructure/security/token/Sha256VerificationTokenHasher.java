package io.github.edtechdevelopment.identity.infrastructure.security.token;

import io.github.edtechdevelopment.identity.application.port.out.security.VerificationTokenHasher;
import io.github.edtechdevelopment.identity.domain.verification.model.VerificationTokenHash;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

@Component
public final class Sha256VerificationTokenHasher implements VerificationTokenHasher {

    private static final String HASH_ALGORITHM = "SHA-256";

    @Override
    public VerificationTokenHash hash(String rawToken) {
        Objects.requireNonNull(rawToken, "Raw verification token must not be null");

        try {
            MessageDigest messageDigest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hash = messageDigest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return new VerificationTokenHash(HexFormat.of().formatHex(hash));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }
}
