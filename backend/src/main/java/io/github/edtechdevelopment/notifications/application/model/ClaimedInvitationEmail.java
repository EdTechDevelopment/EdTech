package io.github.edtechdevelopment.notifications.application.model;

import io.github.edtechdevelopment.notifications.domain.delivery.model.InvitationEmailDelivery;

import java.util.UUID;

public record ClaimedInvitationEmail(InvitationEmailDelivery delivery, UUID claimId) { }
