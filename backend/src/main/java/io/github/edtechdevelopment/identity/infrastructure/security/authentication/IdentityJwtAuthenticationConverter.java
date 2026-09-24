package io.github.edtechdevelopment.identity.infrastructure.security.authentication;

import io.github.edtechdevelopment.identity.domain.user.model.UserRole;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public final class IdentityJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String ROLES_CLAIM = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        try {
            UUID userId = UUID.fromString(jwt.getSubject());
            Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
            return new JwtAuthenticationToken(jwt, authorities, userId.toString());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidBearerTokenException("JWT subject or roles have an invalid format", exception);
        }
    }

    private static Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        List<String> roleNames = jwt.getClaimAsStringList(ROLES_CLAIM);
        if (roleNames == null || roleNames.isEmpty()) {
            throw new IllegalArgumentException("JWT roles claim must not be empty");
        }

        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        for (String roleName : roleNames) {
            UserRole role = UserRole.valueOf(roleName);
            authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + role.name()));
        }
        return List.copyOf(authorities);
    }
}
