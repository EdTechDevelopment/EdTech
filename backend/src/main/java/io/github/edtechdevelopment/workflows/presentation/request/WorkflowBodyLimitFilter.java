package io.github.edtechdevelopment.workflows.presentation.request;

import io.github.edtechdevelopment.workflows.presentation.error.WorkflowApiError;

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
public class WorkflowBodyLimitFilter extends OncePerRequestFilter {

    private final ObjectMapper json;
    private final int maxBytes;

    public WorkflowBodyLimitFilter(ObjectMapper json, @Value("${workflows.http.max-request-bytes:1048576}") int maxBytes) {
        if (maxBytes < 1 || maxBytes == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Request body limit is invalid");
        }
        this.json = json;
        this.maxBytes = maxBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean workflowWrite = request.getMethod().equals("POST")
                && (path.equals("/api/v1/auth/register") || path.equals("/api/v1/me/roles/teacher")
                || path.equals("/api/v1/me/roles/student"));
        if (!workflowWrite) {
            chain.doFilter(request, response);
            return;
        }

        byte[] body = request.getInputStream().readNBytes(maxBytes + 1);
        if (body.length > maxBytes) {
            response.setStatus(413);
            response.setContentType("application/json");
            json.writeValue(response.getOutputStream(), new WorkflowApiError(
                    "REQUEST_TOO_LARGE", "Request body is too large", List.of(), UUID.randomUUID().toString()));
            return;
        }
        chain.doFilter(new BufferedBodyRequest(request, body), response);
    }
}

