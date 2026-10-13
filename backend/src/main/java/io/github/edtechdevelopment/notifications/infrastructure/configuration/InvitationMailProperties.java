package io.github.edtechdevelopment.notifications.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties("notifications.invitation")
public record InvitationMailProperties(URI frontendBaseUrl) {
    public InvitationMailProperties {
        if (frontendBaseUrl == null || frontendBaseUrl.getHost() == null
                || !(frontendBaseUrl.getScheme().equals("http") || frontendBaseUrl.getScheme().equals("https"))
                || frontendBaseUrl.getRawQuery() != null || frontendBaseUrl.getRawFragment() != null) {
            throw new IllegalArgumentException("Invitation frontend base URL must be an absolute HTTP or HTTPS URL without query or fragment");
        }
    }
}
