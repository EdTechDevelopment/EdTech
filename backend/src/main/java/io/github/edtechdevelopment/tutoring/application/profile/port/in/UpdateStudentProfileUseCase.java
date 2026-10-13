package io.github.edtechdevelopment.tutoring.application.profile.port.in;

import io.github.edtechdevelopment.tutoring.application.profile.model.command.UpdateStudentProfileCommand;

public interface UpdateStudentProfileUseCase {

    void execute(UpdateStudentProfileCommand command);
}
