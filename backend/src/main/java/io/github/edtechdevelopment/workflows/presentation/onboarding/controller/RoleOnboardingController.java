package io.github.edtechdevelopment.workflows.presentation.onboarding.controller;

import io.github.edtechdevelopment.workflows.application.onboarding.model.AddStudentRoleCommand;
import io.github.edtechdevelopment.workflows.application.onboarding.model.AddTeacherRoleCommand;
import io.github.edtechdevelopment.workflows.application.onboarding.port.in.AddStudentRoleUseCase;
import io.github.edtechdevelopment.workflows.application.onboarding.port.in.AddTeacherRoleUseCase;
import io.github.edtechdevelopment.workflows.presentation.me.mapper.MePresentationMapper;
import io.github.edtechdevelopment.workflows.presentation.me.model.MeResponse;
import io.github.edtechdevelopment.workflows.presentation.profile.ProfileInputPresentationMapper;
import io.github.edtechdevelopment.workflows.presentation.profile.StudentProfileRequest;
import io.github.edtechdevelopment.workflows.presentation.profile.TeacherProfileRequest;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class RoleOnboardingController {

    private final AddTeacherRoleUseCase teachers;
    private final AddStudentRoleUseCase students;
    private final ProfileInputPresentationMapper profiles;
    private final MePresentationMapper mapper;

    public RoleOnboardingController(
            AddTeacherRoleUseCase teachers, AddStudentRoleUseCase students,
            ProfileInputPresentationMapper profiles, MePresentationMapper mapper) {
        this.teachers = teachers;
        this.students = students;
        this.profiles = profiles;
        this.mapper = mapper;
    }

    @PostMapping("/api/v1/me/roles/teacher")
    public ResponseEntity<MeResponse> addTeacher(
            Authentication actor, @RequestHeader("Idempotency-Key") UUID operationId,
            @Valid @RequestBody TeacherProfileRequest request) {
        var result = teachers.addTeacherRole(new AddTeacherRoleCommand(
                operationId, UUID.fromString(actor.getName()), profiles.toData(request)));
        return ResponseEntity.status(201).body(mapper.toResponse(result));
    }

    @PostMapping("/api/v1/me/roles/student")
    public ResponseEntity<MeResponse> addStudent(
            Authentication actor, @RequestHeader("Idempotency-Key") UUID operationId,
            @Valid @RequestBody StudentProfileRequest request) {
        var result = students.addStudentRole(new AddStudentRoleCommand(
                operationId, UUID.fromString(actor.getName()), profiles.toData(request)));
        return ResponseEntity.status(201).body(mapper.toResponse(result));
    }
}

