package io.github.edtechdevelopment.tutoring.api.command.relationship;

public interface TutoringRelationshipCommands {

    TeacherStudentRemovalResult removeTeacherStudent(RemoveTeacherStudentCommand command);
}
