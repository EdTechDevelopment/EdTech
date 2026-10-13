package io.github.edtechdevelopment.tutoring.presentation.invitation.controller;

import io.github.edtechdevelopment.tutoring.application.invitation.model.command.AcceptStudentInvitationCommand;
import io.github.edtechdevelopment.tutoring.application.invitation.model.command.CreateStudentInvitationCommand;
import io.github.edtechdevelopment.tutoring.application.invitation.model.command.RejectStudentInvitationCommand;
import io.github.edtechdevelopment.tutoring.application.invitation.model.query.IncomingInvitationsQuery;
import io.github.edtechdevelopment.tutoring.application.invitation.model.query.SentInvitationsQuery;
import io.github.edtechdevelopment.tutoring.application.invitation.port.in.AcceptStudentInvitationUseCase;
import io.github.edtechdevelopment.tutoring.application.invitation.port.in.CreateStudentInvitationUseCase;
import io.github.edtechdevelopment.tutoring.application.invitation.port.in.ListIncomingInvitationsUseCase;
import io.github.edtechdevelopment.tutoring.application.invitation.port.in.ListSentInvitationsUseCase;
import io.github.edtechdevelopment.tutoring.application.invitation.port.in.RejectStudentInvitationUseCase;
import io.github.edtechdevelopment.tutoring.presentation.invitation.model.CreateInvitationRequest;
import io.github.edtechdevelopment.tutoring.presentation.invitation.model.IncomingInvitationResponse;
import io.github.edtechdevelopment.tutoring.presentation.invitation.model.InvitationAcceptedResponse;
import io.github.edtechdevelopment.tutoring.presentation.invitation.model.InvitationCreatedResponse;
import io.github.edtechdevelopment.tutoring.presentation.invitation.model.InvitationRejectedResponse;
import io.github.edtechdevelopment.tutoring.presentation.invitation.model.SentInvitationResponse;
import io.github.edtechdevelopment.tutoring.presentation.invitation.model.TeacherSummaryResponse;
import io.github.edtechdevelopment.tutoring.presentation.model.PageResponse;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tutoring/invitations")
public class InvitationController {
    private final CreateStudentInvitationUseCase create;
    private final AcceptStudentInvitationUseCase accept;
    private final RejectStudentInvitationUseCase reject;
    private final ListSentInvitationsUseCase sent;
    private final ListIncomingInvitationsUseCase incoming;
    public InvitationController(CreateStudentInvitationUseCase create, AcceptStudentInvitationUseCase accept,
                                RejectStudentInvitationUseCase reject, ListSentInvitationsUseCase sent, ListIncomingInvitationsUseCase incoming) {
        this.create = create;
        this.accept = accept;
        this.reject = reject;
        this.sent = sent;
        this.incoming = incoming;
    }
    private UUID operationId(String value) {
        try {
            return UUID.fromString(value);
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Idempotency-Key must be a UUID");
        }
    }

    @PostMapping
    public ResponseEntity<InvitationCreatedResponse> create(Authentication actor, @RequestHeader("Idempotency-Key") String key,
                                                            @Valid @RequestBody CreateInvitationRequest request) {
        var result = create.execute(new CreateStudentInvitationCommand(operationId(key), UUID.fromString(actor.getName()), request.studentEmail()));
        return ResponseEntity.status(HttpStatus.CREATED).body(new InvitationCreatedResponse(result.invitationId(), result.expiresAt()));
    }
    @PostMapping("/{id}/accept")
    public InvitationAcceptedResponse accept(Authentication actor, @PathVariable UUID id, @RequestHeader("Idempotency-Key") String key) {
        var result = accept.execute(new AcceptStudentInvitationCommand(operationId(key), id, UUID.fromString(actor.getName())));
        return new InvitationAcceptedResponse(result.invitationId(), result.teacherUserId(), result.studentUserId(), result.linkedAt());
    }
    @PostMapping("/{id}/reject")
    public InvitationRejectedResponse reject(Authentication actor, @PathVariable UUID id, @RequestHeader("Idempotency-Key") String key) {
        var result = reject.execute(new RejectStudentInvitationCommand(operationId(key), id, UUID.fromString(actor.getName())));
        return new InvitationRejectedResponse(result.invitationId(), result.status());
    }
    @GetMapping("/sent")
    public PageResponse<SentInvitationResponse> sent(Authentication actor, @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "50") int limit) {
        var page = sent.execute(SentInvitationsQuery.fromStatus(UUID.fromString(actor.getName()), status, cursor, limit));
        return new PageResponse<>(page.items().stream().map(item -> new SentInvitationResponse(item.invitationId(),
                item.studentEmail(), item.status(), item.createdAt(), item.expiresAt(), item.respondedAt())).toList(), page.nextCursor());
    }
    @GetMapping("/incoming")
    public PageResponse<IncomingInvitationResponse> incoming(Authentication actor, @RequestParam(required = false) String status,
                                                            @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "50") int limit) {
        var page = incoming.execute(IncomingInvitationsQuery.fromStatus(UUID.fromString(actor.getName()), status, cursor, limit));
        return new PageResponse<>(page.items().stream().map(item -> new IncomingInvitationResponse(item.invitationId(),
                new TeacherSummaryResponse(item.teacher().userId(), item.teacher().displayName()), item.status(),
                item.createdAt(), item.expiresAt(), item.respondedAt())).toList(), page.nextCursor());
    }
}
