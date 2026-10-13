package io.github.edtechdevelopment.tutoring.application.relationship.port.in;

import io.github.edtechdevelopment.tutoring.application.relationship.model.query.ListStudentTeachersQuery;
import io.github.edtechdevelopment.tutoring.application.relationship.model.result.TeacherRelationshipPageResult;

public interface ListStudentTeachersUseCase {

    TeacherRelationshipPageResult execute(ListStudentTeachersQuery query);
}
