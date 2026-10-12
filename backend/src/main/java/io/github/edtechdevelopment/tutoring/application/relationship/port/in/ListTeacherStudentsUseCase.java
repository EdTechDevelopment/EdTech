package io.github.edtechdevelopment.tutoring.application.relationship.port.in;

import io.github.edtechdevelopment.tutoring.application.relationship.model.query.ListTeacherStudentsQuery;
import io.github.edtechdevelopment.tutoring.application.relationship.model.result.StudentRelationshipPageResult;

public interface ListTeacherStudentsUseCase {

    StudentRelationshipPageResult execute(ListTeacherStudentsQuery query);
}
