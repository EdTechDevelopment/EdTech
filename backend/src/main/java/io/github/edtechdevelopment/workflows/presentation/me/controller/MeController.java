package io.github.edtechdevelopment.workflows.presentation.me.controller;

import io.github.edtechdevelopment.workflows.application.me.port.in.GetMeUseCase;
import io.github.edtechdevelopment.workflows.presentation.me.mapper.MePresentationMapper;
import io.github.edtechdevelopment.workflows.presentation.me.model.MeResponse;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class MeController {

    private final GetMeUseCase me;
    private final MePresentationMapper mapper;

    public MeController(GetMeUseCase me, MePresentationMapper mapper) {
        this.me = me;
        this.mapper = mapper;
    }

    @GetMapping("/api/v1/me")
    public MeResponse getMe(Authentication actor) {
        return mapper.toResponse(me.getMe(UUID.fromString(actor.getName())));
    }
}

