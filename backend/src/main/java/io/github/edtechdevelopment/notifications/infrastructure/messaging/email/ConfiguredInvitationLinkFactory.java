package io.github.edtechdevelopment.notifications.infrastructure.messaging.email;

import io.github.edtechdevelopment.notifications.application.port.out.email.InvitationLinkFactory;
import io.github.edtechdevelopment.notifications.infrastructure.configuration.InvitationMailProperties;

import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

public class ConfiguredInvitationLinkFactory implements InvitationLinkFactory {
    private final URI baseUrl;
    public ConfiguredInvitationLinkFactory(InvitationMailProperties properties) {
        this.baseUrl = properties.frontendBaseUrl();
    }

    @Override
    public URI create(UUID invitationId, String recipientEmail) {
        return UriComponentsBuilder.fromUri(baseUrl).pathSegment("invitations", invitationId.toString())
                .queryParam("email", "{email}").encode().buildAndExpand(recipientEmail).toUri();
    }
}
