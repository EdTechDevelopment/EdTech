package io.github.edtechdevelopment.tutoring.application.invitation.model.command;

import java.util.Objects;
import java.util.UUID;

public record CreateStudentInvitationCommand(UUID operationId, UUID teacherUserId, String studentEmail) {

    public CreateStudentInvitationCommand {
        Objects.requireNonNull(operationId, "Operation id must not be null");
        Objects.requireNonNull(teacherUserId, "Teacher user id must not be null");
        Objects.requireNonNull(studentEmail, "Student email must not be null");
        if (studentEmail.isBlank()) {
            throw new IllegalArgumentException("Student email must not be blank");
        }
    }
}
