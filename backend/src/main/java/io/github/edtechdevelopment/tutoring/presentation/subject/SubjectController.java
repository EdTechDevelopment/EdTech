package io.github.edtechdevelopment.tutoring.presentation.subject;

import io.github.edtechdevelopment.tutoring.application.subject.port.in.GetSubjectsUseCase;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tutoring/subjects")
public class SubjectController {
    private final GetSubjectsUseCase subjects;
    public SubjectController(GetSubjectsUseCase subjects) {
        this.subjects = subjects;
    }

    @GetMapping
    public List<SubjectResponse> getSubjects() {
        return subjects.getSubjects().stream().map(subject -> new SubjectResponse(subject.subjectCode(), subject.name())).toList();
    }
}
