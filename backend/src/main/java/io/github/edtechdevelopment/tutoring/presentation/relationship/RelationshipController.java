package io.github.edtechdevelopment.tutoring.presentation.relationship;

import io.github.edtechdevelopment.tutoring.application.relationship.model.query.ListStudentTeachersQuery;
import io.github.edtechdevelopment.tutoring.application.relationship.model.query.ListTeacherStudentsQuery;
import io.github.edtechdevelopment.tutoring.application.relationship.port.in.ListStudentTeachersUseCase;
import io.github.edtechdevelopment.tutoring.application.relationship.port.in.ListTeacherStudentsUseCase;
import io.github.edtechdevelopment.tutoring.presentation.model.PageResponse;
import io.github.edtechdevelopment.tutoring.presentation.profile.mapper.ProfilePresentationMapper;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tutoring/relationships")
public class RelationshipController {
    private final ListTeacherStudentsUseCase students;
    private final ListStudentTeachersUseCase teachers;
    private final ProfilePresentationMapper mapper;
    public RelationshipController(ListTeacherStudentsUseCase students, ListStudentTeachersUseCase teachers, ProfilePresentationMapper mapper) {
        this.students = students;
        this.teachers = teachers;
        this.mapper = mapper;
    }
    @GetMapping("/students")
    public PageResponse<StudentRelationshipResponse> students(Authentication actor, @RequestParam(required = false) String cursor,
                                                              @RequestParam(defaultValue = "50") int limit) {
        var page = students.execute(new ListTeacherStudentsQuery(UUID.fromString(actor.getName()), cursor, limit));
        return new PageResponse<>(page.items().stream().map(item ->
                new StudentRelationshipResponse(item.linkedAt(), mapper.toResponse(item.student()))).toList(), page.nextCursor());
    }
    @GetMapping("/teachers")
    public PageResponse<TeacherRelationshipResponse> teachers(Authentication actor, @RequestParam(required = false) String cursor,
                                                              @RequestParam(defaultValue = "50") int limit) {
        var page = teachers.execute(new ListStudentTeachersQuery(UUID.fromString(actor.getName()), cursor, limit));
        return new PageResponse<>(page.items().stream().map(item ->
                new TeacherRelationshipResponse(item.linkedAt(), mapper.toResponse(item.teacher()))).toList(), page.nextCursor());
    }
}
