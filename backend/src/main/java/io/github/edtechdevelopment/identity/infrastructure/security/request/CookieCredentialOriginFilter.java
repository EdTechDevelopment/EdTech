package io.github.edtechdevelopment.identity.infrastructure.security.request;

import io.github.edtechdevelopment.identity.infrastructure.security.configuration.IdentityCorsProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;

public final class CookieCredentialOriginFilter extends OncePerRequestFilter {

    private static final Set<String> COOKIE_CREDENTIAL_ENDPOINTS = Set.of(
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout"
    );

    private final Set<String> allowedOrigins;
    private final AccessDeniedHandler accessDeniedHandler;

    public CookieCredentialOriginFilter(
            IdentityCorsProperties corsProperties,
            AccessDeniedHandler accessDeniedHandler
    ) {
        Objects.requireNonNull(corsProperties, "Identity CORS properties must not be null");
        this.allowedOrigins = Set.copyOf(corsProperties.allowedOrigins());
        this.accessDeniedHandler = Objects.requireNonNull(
                accessDeniedHandler,
                "Access denied handler must not be null"
        );
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod())
                || !COOKIE_CREDENTIAL_ENDPOINTS.contains(requestPath(request));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null || !allowedOrigins.contains(origin)) {
            accessDeniedHandler.handle(
                    request,
                    response,
                    new AccessDeniedException("Cookie credential endpoint requires an allowed Origin")
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static String requestPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath.isEmpty()
                ? requestUri
                : requestUri.substring(contextPath.length());
    }
}
