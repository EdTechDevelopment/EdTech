package io.github.edtechdevelopment.tutoring.application.profile.port.in;

import io.github.edtechdevelopment.tutoring.application.profile.model.command.UpdateTeacherProfileCommand;

public interface UpdateTeacherProfileUseCase {

    void execute(UpdateTeacherProfileCommand command);
}
