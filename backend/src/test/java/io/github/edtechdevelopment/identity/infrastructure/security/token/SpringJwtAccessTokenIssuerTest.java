package io.github.edtechdevelopment.identity.infrastructure.security.token;

import io.github.edtechdevelopment.identity.application.model.IssuedAccessToken;
import io.github.edtechdevelopment.identity.domain.user.model.Email;
import io.github.edtechdevelopment.identity.domain.user.model.PasswordHash;
import io.github.edtechdevelopment.identity.domain.user.model.User;
import io.github.edtechdevelopment.identity.domain.user.model.UserRole;
import io.github.edtechdevelopment.identity.domain.user.model.UserStatus;
import io.github.edtechdevelopment.identity.infrastructure.security.configuration.IdentityTokenProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpringJwtAccessTokenIssuerTest {

    @Test
    void issuesVerifiableRs256JwtWithRequiredClaims() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        SpringJwtAccessTokenIssuer tokenIssuer = new SpringJwtAccessTokenIssuer(
                NimbusJwtEncoder.withKeyPair(publicKey, privateKey).build(),
                tokenProperties()
        );
        NimbusJwtDecoder tokenDecoder = NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        UUID userId = UUID.fromString("c8dce42b-5e7b-4f72-83a4-5f6d19a327c8");
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        IssuedAccessToken issuedToken = tokenIssuer.issue(activeUser(userId, issuedAt), issuedAt);
        Jwt decodedToken = tokenDecoder.decode(issuedToken.value());

        assertEquals("RS256", decodedToken.getHeaders().get("alg"));
        assertEquals("JWT", decodedToken.getHeaders().get("typ"));
        assertEquals(userId.toString(), decodedToken.getSubject());
        assertEquals("edtech-backend", decodedToken.getClaimAsString("iss"));
        assertEquals(List.of("edtech-api"), decodedToken.getAudience());
        assertEquals(issuedAt, decodedToken.getIssuedAt());
        assertEquals(issuedAt.plus(Duration.ofMinutes(15)), decodedToken.getExpiresAt());
        assertEquals(List.of("STUDENT", "TEACHER"), decodedToken.getClaimAsStringList("roles"));
        assertEquals(issuedToken.expiresAt(), decodedToken.getExpiresAt());
    }

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        return keyPairGenerator.generateKeyPair();
    }

    private static User activeUser(UUID userId, Instant createdAt) {
        return User.reconstitute(
                userId,
                new Email("student@example.com"),
                null,
                new PasswordHash("encoded-password"),
                "Student",
                "Example",
                LocalDate.of(2000, 1, 1),
                Set.of(UserRole.TEACHER, UserRole.STUDENT),
                UserStatus.ACTIVE,
                createdAt,
                createdAt,
                createdAt
        );
    }

    private static IdentityTokenProperties tokenProperties() {
        return new IdentityTokenProperties(
                Duration.ofMinutes(5),
                32,
                Duration.ofMinutes(15),
                Duration.ofDays(30),
                32,
                "edtech-backend",
                "edtech-api"
        );
    }
}
