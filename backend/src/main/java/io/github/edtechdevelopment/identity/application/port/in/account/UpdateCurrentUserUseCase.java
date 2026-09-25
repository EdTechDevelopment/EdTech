package io.github.edtechdevelopment.identity.application.port.in.account;

import io.github.edtechdevelopment.identity.application.command.account.UpdateCurrentUserCommand;
import io.github.edtechdevelopment.identity.application.result.CurrentUserResult;

public interface UpdateCurrentUserUseCase {

    CurrentUserResult updateCurrentUser(UpdateCurrentUserCommand command);
}
