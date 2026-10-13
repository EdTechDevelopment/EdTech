package io.github.edtechdevelopment.workflows.application.me.port.in;

import io.github.edtechdevelopment.workflows.application.me.model.MeResult;

import java.util.UUID;

public interface GetMeUseCase {

    MeResult getMe(UUID actorUserId);
}

