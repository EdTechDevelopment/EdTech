package io.github.edtechdevelopment.identity.infrastructure.security.request;

import io.github.edtechdevelopment.identity.api.model.UserRoleView;
import io.github.edtechdevelopment.identity.api.model.UserStatusView;
import io.github.edtechdevelopment.identity.api.model.UserSummary;
import io.github.edtechdevelopment.identity.api.query.IdentityQuery;
import io.github.edtechdevelopment.identity.presentation.error.model.ApiError;
import io.github.edtechdevelopment.identity.presentation.error.model.ErrorCode;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class TutoringAccountAccessFilter extends OncePerRequestFilter {
    private final ObjectProvider<IdentityQuery> identity;
    private final ObjectMapper json;
    public TutoringAccountAccessFilter(ObjectProvider<IdentityQuery> identity, ObjectMapper json) {
        this.identity = identity;
        this.json = json;
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean publicRead = request.getMethod().equals("GET") && (path.equals("/api/v1/tutoring/subjects")
                || path.startsWith("/api/v1/tutoring/profiles/teachers") || path.startsWith("/api/v1/tutoring/profiles/students"));
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!path.startsWith("/api/v1/tutoring/") || publicRead || authentication == null || !authentication.isAuthenticated()) {
            chain.doFilter(request, response); return;
        }
        Optional<UserSummary> account;
        try {
            account = identity.getObject().findUserById(UUID.fromString(authentication.getName()));
        }
        catch (RuntimeException exception) {
            writeError(response, 503, "Account authorization is temporarily unavailable"); return;
        }
        boolean allowed = account.isPresent() && account.get().status() == UserStatusView.ACTIVE;
        if (allowed && ((request.getMethod().equals("POST") && path.equals("/api/v1/tutoring/invitations"))
                || (request.getMethod().equals("PUT") && path.equals("/api/v1/tutoring/profiles/teacher")))) {
            allowed = account.get().roles().contains(UserRoleView.TEACHER);
        }
        if (allowed && request.getMethod().equals("PUT") && path.equals("/api/v1/tutoring/profiles/student")) {
            allowed = account.get().roles().contains(UserRoleView.STUDENT);
        }
        if (!allowed) {
            writeError(response, 403, "Access is denied");
            return;
        }
        chain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        json.writeValue(response.getOutputStream(), new ApiError(status == 403 ? ErrorCode.FORBIDDEN : ErrorCode.INTERNAL_ERROR,
                message, List.of(), UUID.randomUUID().toString()));
    }
}
