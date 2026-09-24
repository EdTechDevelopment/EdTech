package io.github.edtechdevelopment.identity.infrastructure.security.configuration;

import io.github.edtechdevelopment.identity.infrastructure.security.authentication.IdentityJwtAuthenticationConverter;
import io.github.edtechdevelopment.identity.presentation.auth.cookie.RefreshTokenCookieFactory;
import io.github.edtechdevelopment.identity.presentation.error.handler.RestAccessDeniedHandler;
import io.github.edtechdevelopment.identity.presentation.error.handler.RestAuthenticationEntryPoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityTestController.class)
@ImportAutoConfiguration(ServletWebSecurityAutoConfiguration.class)
@Import({
        SecurityConfiguration.class,
        IdentityJwtAuthenticationConverter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityConfigurationWebTest.SecurityTestConfiguration.class
})
class SecurityConfigurationWebTest {

    private static final String USER_ID = "c8dce42b-5e7b-4f72-83a4-5f6d19a327c8";

    private final org.springframework.test.web.servlet.MockMvc mockMvc;

    @Autowired
    SecurityConfigurationWebTest(org.springframework.test.web.servlet.MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void leavesExplicitAuthenticationEndpointPublic() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsRefreshWithoutOrigin() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Access is denied"));
    }

    @Test
    void rejectsRefreshFromUnconfiguredOrigin() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header(HttpHeaders.ORIGIN, "https://evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void allowsRefreshRequestFromConfiguredOriginToReachMvc() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header(HttpHeaders.ORIGIN, "http://frontend.example:3000"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://frontend.example:3000"
                ));
    }

    @Test
    void rejectsLogoutWithoutOrigin() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void allowsLogoutRequestFromConfiguredOriginToReachMvc() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.ORIGIN, "http://frontend.example:3000"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://frontend.example:3000"
                ));
    }

    @Test
    void returnsApiErrorWhenProtectedEndpointHasNoAccessToken() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("Authentication is required"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void acceptsValidBearerTokenAndExposesAuthenticatedUserId() throws Exception {
        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(content().string(USER_ID));
    }

    @Test
    void rejectsInvalidBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void allowsCorsPreflightFromConfiguredFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/me")
                        .header(HttpHeaders.ORIGIN, "http://frontend.example:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://frontend.example:3000"
                ));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SecurityTestConfiguration {

        @Bean
        IdentityCorsProperties identityCorsProperties() {
            return new IdentityCorsProperties(List.of("http://frontend.example:3000"));
        }

        @Bean
        RefreshTokenCookieFactory refreshTokenCookieFactory() {
            return new RefreshTokenCookieFactory(Clock.systemUTC(), false);
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                if (!"valid-token".equals(token)) {
                    throw new BadJwtException("Invalid test token");
                }
                Instant now = Instant.now();
                return Jwt.withTokenValue(token)
                        .header("alg", "RS256")
                        .subject(USER_ID)
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(900))
                        .claim("roles", List.of("STUDENT"))
                        .build();
            };
        }
    }
}
