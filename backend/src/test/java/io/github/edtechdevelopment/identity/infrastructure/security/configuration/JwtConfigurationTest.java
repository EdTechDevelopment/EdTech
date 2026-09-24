package io.github.edtechdevelopment.identity.infrastructure.security.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtConfigurationTest {

    private final JwtConfiguration configuration = new JwtConfiguration();

    @Test
    void decoderAcceptsValidRs256TokenWithoutIssuerOrAudienceValidation() throws Exception {
        KeyPair keyPair = generateKeyPair();
        JwtEncoder encoder = configuration.jwtEncoder(publicKey(keyPair), privateKey(keyPair));
        JwtDecoder decoder = configuration.jwtDecoder(publicKey(keyPair));
        Instant now = Instant.now();

        String token = encode(
                encoder,
                claims(now, now.plus(Duration.ofMinutes(15)), "another-issuer", "another-api")
        );

        assertDoesNotThrow(() -> decoder.decode(token));
    }

    @Test
    void decoderRejectsExpiredToken() throws Exception {
        KeyPair keyPair = generateKeyPair();
        JwtEncoder encoder = configuration.jwtEncoder(publicKey(keyPair), privateKey(keyPair));
        JwtDecoder decoder = configuration.jwtDecoder(publicKey(keyPair));
        Instant issuedAt = Instant.now().minus(Duration.ofMinutes(20));

        String token = encode(
                encoder,
                claims(issuedAt, issuedAt.plus(Duration.ofMinutes(15)), "edtech-backend", "edtech-api")
        );

        assertThrows(JwtException.class, () -> decoder.decode(token));
    }

    @Test
    void decoderRejectsTokenSignedWithAnotherPrivateKey() throws Exception {
        KeyPair trustedKeyPair = generateKeyPair();
        KeyPair foreignKeyPair = generateKeyPair();
        JwtDecoder decoder = configuration.jwtDecoder(publicKey(trustedKeyPair));
        JwtEncoder foreignEncoder = configuration.jwtEncoder(
                publicKey(foreignKeyPair),
                privateKey(foreignKeyPair)
        );
        Instant now = Instant.now();

        String token = encode(
                foreignEncoder,
                claims(now, now.plus(Duration.ofMinutes(15)), "edtech-backend", "edtech-api")
        );

        assertThrows(JwtException.class, () -> decoder.decode(token));
    }

    @Test
    void decoderRejectsTokenWithoutExpiration() throws Exception {
        KeyPair keyPair = generateKeyPair();
        JwtEncoder encoder = configuration.jwtEncoder(publicKey(keyPair), privateKey(keyPair));
        JwtDecoder decoder = configuration.jwtDecoder(publicKey(keyPair));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(now)
                .claim("roles", List.of("STUDENT"))
                .build();

        String token = encode(encoder, claims);

        assertThrows(JwtException.class, () -> decoder.decode(token));
    }

    private static JwtClaimsSet claims(
            Instant issuedAt,
            Instant expiresAt,
            String issuer,
            String audience
    ) {
        return JwtClaimsSet.builder()
                .subject(UUID.randomUUID().toString())
                .issuer(issuer)
                .audience(List.of(audience))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("roles", List.of("STUDENT"))
                .build();
    }

    private static String encode(JwtEncoder encoder, JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .type("JWT")
                .build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static RSAPublicKey publicKey(KeyPair keyPair) {
        return (RSAPublicKey) keyPair.getPublic();
    }

    private static RSAPrivateKey privateKey(KeyPair keyPair) {
        return (RSAPrivateKey) keyPair.getPrivate();
    }
}
