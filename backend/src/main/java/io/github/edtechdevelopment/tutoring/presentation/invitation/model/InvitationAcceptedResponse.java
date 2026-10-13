package io.github.edtechdevelopment.tutoring.presentation.invitation.model;

import java.time.Instant;
import java.util.UUID;
public record InvitationAcceptedResponse(UUID invitationId, UUID teacherUserId, UUID studentUserId, Instant linkedAt) { }
