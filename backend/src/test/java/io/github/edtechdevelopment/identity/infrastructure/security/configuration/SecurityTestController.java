package io.github.edtechdevelopment.identity.infrastructure.security.configuration;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class SecurityTestController {

    @GetMapping("/api/v1/me")
    String currentUser(Authentication authentication) {
        return authentication.getName();
    }
}
