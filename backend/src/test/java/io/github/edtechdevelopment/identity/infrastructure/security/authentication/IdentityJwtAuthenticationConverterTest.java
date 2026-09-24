package io.github.edtechdevelopment.identity.infrastructure.security.authentication;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdentityJwtAuthenticationConverterTest {

    private final IdentityJwtAuthenticationConverter converter = new IdentityJwtAuthenticationConverter();

    @Test
    void convertsSubjectAndRolesToSpringAuthentication() {
        UUID userId = UUID.fromString("c8dce42b-5e7b-4f72-83a4-5f6d19a327c8");
        Jwt jwt = jwt(userId.toString(), List.of("STUDENT", "TEACHER"));

        AbstractAuthenticationToken authentication = converter.convert(jwt);

        assertEquals(userId.toString(), authentication.getName());
        assertEquals(
                List.of("ROLE_STUDENT", "ROLE_TEACHER"),
                authentication.getAuthorities().stream()
                        .map(authority -> authority.getAuthority())
                        .toList()
        );
    }

    @Test
    void rejectsSubjectThatIsNotUuid() {
        assertThrows(
                InvalidBearerTokenException.class,
                () -> converter.convert(jwt("not-a-uuid", List.of("STUDENT")))
        );
    }

    @Test
    void rejectsUnknownRole() {
        assertThrows(
                InvalidBearerTokenException.class,
                () -> converter.convert(jwt(UUID.randomUUID().toString(), List.of("SUPER_ADMIN")))
        );
    }

    private static Jwt jwt(String subject, List<String> roles) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(900))
                .claim("roles", roles)
                .build();
    }
}
