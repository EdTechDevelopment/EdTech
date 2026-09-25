package io.github.edtechdevelopment.identity.infrastructure.security.token;

import io.github.edtechdevelopment.identity.application.model.IssuedAccessToken;
import io.github.edtechdevelopment.identity.application.port.out.security.AccessTokenIssuer;
import io.github.edtechdevelopment.identity.domain.user.model.User;
import io.github.edtechdevelopment.identity.infrastructure.security.configuration.IdentityTokenProperties;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class SpringJwtAccessTokenIssuer implements AccessTokenIssuer {

    private static final String ROLES_CLAIM = "roles";

    private final JwtEncoder jwtEncoder;
    private final IdentityTokenProperties properties;

    public SpringJwtAccessTokenIssuer(JwtEncoder jwtEncoder, IdentityTokenProperties properties) {
        this.jwtEncoder = Objects.requireNonNull(jwtEncoder, "JWT encoder must not be null");
        this.properties = Objects.requireNonNull(properties, "Identity token properties must not be null");
    }

    @Override
    public IssuedAccessToken issue(User user, Instant issuedAt) {
        Objects.requireNonNull(user, "User must not be null");
        Objects.requireNonNull(issuedAt, "Access token issue time must not be null");

        Instant expiresAt = issuedAt.plus(properties.accessTtl());
        List<String> roles = user.roles().stream()
                .map(Enum::name)
                .sorted()
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.id().toString())
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim(ROLES_CLAIM, roles)
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .type("JWT")
                .build();

        String jwtToken = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(jwtToken, expiresAt);
    }
}
