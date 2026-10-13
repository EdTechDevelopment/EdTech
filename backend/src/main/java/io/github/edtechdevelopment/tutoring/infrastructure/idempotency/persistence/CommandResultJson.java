package io.github.edtechdevelopment.tutoring.infrastructure.idempotency.persistence;

import io.github.edtechdevelopment.tutoring.api.command.relationship.TeacherStudentRemovalResult;
import io.github.edtechdevelopment.tutoring.application.idempotency.model.OperationType;
import io.github.edtechdevelopment.tutoring.application.invitation.model.result.InvitationAcceptedResult;
import io.github.edtechdevelopment.tutoring.application.invitation.model.result.InvitationCreatedResult;
import io.github.edtechdevelopment.tutoring.application.invitation.model.result.InvitationRejectedResult;
import io.github.edtechdevelopment.tutoring.infrastructure.persistence.InvalidPersistenceDataException;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class CommandResultJson {
    private final ObjectMapper json;
    public CommandResultJson(ObjectMapper json) {
        this.json = json;
    }

    private Class<?> resultType(OperationType operation) {
        return switch (operation) {
            case CREATE_STUDENT_INVITATION -> InvitationCreatedResult.class;
            case ACCEPT_STUDENT_INVITATION -> InvitationAcceptedResult.class;
            case REJECT_STUDENT_INVITATION -> InvitationRejectedResult.class;
            case REMOVE_TEACHER_STUDENT -> TeacherStudentRemovalResult.class;
        };
    }
    public String encode(OperationType operation, Object result) {
        if (!resultType(operation).isInstance(result)) {
            throw new InvalidPersistenceDataException("Unexpected command result type");
        }
        return json.writeValueAsString(result);
    }
    public Object decode(OperationType operation, String type, int version, String payload) {
        if (version != 1 || !resultType(operation).getName().equals(type)) {
            throw new InvalidPersistenceDataException("Unsupported stored command result");
        }
        try {
            return json.readValue(payload, resultType(operation));
        }
        catch (RuntimeException exception) {
            throw new InvalidPersistenceDataException("Stored command result is invalid", exception);
        }
    }
}
