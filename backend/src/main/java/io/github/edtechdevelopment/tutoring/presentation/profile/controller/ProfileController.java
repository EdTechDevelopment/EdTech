package io.github.edtechdevelopment.tutoring.presentation.profile.controller;

import io.github.edtechdevelopment.tutoring.application.profile.model.query.PublicProfileSearchQuery;
import io.github.edtechdevelopment.tutoring.application.profile.port.in.GetPublicProfileUseCase;
import io.github.edtechdevelopment.tutoring.application.profile.port.in.SearchPublicProfilesUseCase;
import io.github.edtechdevelopment.tutoring.application.profile.port.in.UpdateStudentProfileUseCase;
import io.github.edtechdevelopment.tutoring.application.profile.port.in.UpdateTeacherProfileUseCase;
import io.github.edtechdevelopment.tutoring.presentation.model.PageResponse;
import io.github.edtechdevelopment.tutoring.presentation.profile.mapper.ProfilePresentationMapper;
import io.github.edtechdevelopment.tutoring.presentation.profile.model.StudentProfileCardResponse;
import io.github.edtechdevelopment.tutoring.presentation.profile.model.StudentProfileResponse;
import io.github.edtechdevelopment.tutoring.presentation.profile.model.TeacherProfileCardResponse;
import io.github.edtechdevelopment.tutoring.presentation.profile.model.TeacherProfileResponse;
import io.github.edtechdevelopment.tutoring.presentation.profile.model.UpdateStudentProfileRequest;
import io.github.edtechdevelopment.tutoring.presentation.profile.model.UpdateTeacherProfileRequest;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tutoring/profiles")
public class ProfileController {
    private final SearchPublicProfilesUseCase search;
    private final GetPublicProfileUseCase profiles;
    private final UpdateTeacherProfileUseCase teachers;
    private final UpdateStudentProfileUseCase students;
    private final ProfilePresentationMapper mapper;
    public ProfileController(SearchPublicProfilesUseCase search, GetPublicProfileUseCase profiles,
                             UpdateTeacherProfileUseCase teachers, UpdateStudentProfileUseCase students, ProfilePresentationMapper mapper) {
        this.search = search;
        this.profiles = profiles;
        this.teachers = teachers;
        this.students = students;
        this.mapper = mapper;
    }
    @GetMapping("/teachers")
    public PageResponse<TeacherProfileCardResponse> searchTeachers(@RequestParam(required = false) String subjectCode,
            @RequestParam(required = false) Integer minAge, @RequestParam(required = false) Integer maxAge,
            @RequestParam(required = false) String cursor) {
        var page = search.searchTeachers(new PublicProfileSearchQuery(subjectCode, minAge, maxAge, cursor));
        return new PageResponse<>(page.items().stream().map(mapper::toResponse).toList(), page.nextCursor());
    }
    @GetMapping("/students")
    public PageResponse<StudentProfileCardResponse> searchStudents(@RequestParam(required = false) String subjectCode,
            @RequestParam(required = false) Integer minAge, @RequestParam(required = false) Integer maxAge,
            @RequestParam(required = false) String cursor) {
        var page = search.searchStudents(new PublicProfileSearchQuery(subjectCode, minAge, maxAge, cursor));
        return new PageResponse<>(page.items().stream().map(mapper::toResponse).toList(), page.nextCursor());
    }
    @GetMapping("/teachers/{userId}")
    public TeacherProfileResponse getTeacher(@PathVariable UUID userId) {
        return mapper.toResponse(profiles.findPublicTeacherProfile(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found")));
    }
    @GetMapping("/students/{userId}")
    public StudentProfileResponse getStudent(@PathVariable UUID userId) {
        return mapper.toResponse(profiles.findPublicStudentProfile(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found")));
    }
    @PutMapping("/teacher")
    public ResponseEntity<Void> updateTeacher(Authentication authentication, @Valid @RequestBody UpdateTeacherProfileRequest request) {
        teachers.execute(mapper.toCommand(UUID.fromString(authentication.getName()), request));
        return ResponseEntity.noContent().build();
    }
    @PutMapping("/student")
    public ResponseEntity<Void> updateStudent(Authentication authentication, @Valid @RequestBody UpdateStudentProfileRequest request) {
        students.execute(mapper.toCommand(UUID.fromString(authentication.getName()), request));
        return ResponseEntity.noContent().build();
    }
}
