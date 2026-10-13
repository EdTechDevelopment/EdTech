package io.github.edtechdevelopment.tutoring.presentation.request;

import io.github.edtechdevelopment.tutoring.presentation.error.TutoringApiError;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
@Order(0)
public class TutoringBodyLimitFilter extends OncePerRequestFilter {
    private final ObjectMapper json;
    private final int maxBytes;
    public TutoringBodyLimitFilter(ObjectMapper json, @Value("${tutoring.http.max-request-bytes:1048576}") int maxBytes) {
        if (maxBytes < 1 || maxBytes == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Request body limit is invalid");
        }
        this.json = json;
        this.maxBytes = maxBytes;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.startsWith("/api/v1/tutoring/")
                || !(request.getMethod().equals("POST") || request.getMethod().equals("PUT"))) {
            chain.doFilter(request, response); return;
        }
        byte[] body = request.getInputStream().readNBytes(maxBytes + 1);
        if (body.length > maxBytes) {
            response.setStatus(413); response.setContentType("application/json");
            json.writeValue(response.getOutputStream(), new TutoringApiError("REQUEST_TOO_LARGE", "Request body is too large", List.of(), UUID.randomUUID().toString()));
            return;
        }
        chain.doFilter(new BufferedBodyRequest(request, body), response);
    }
}
