package io.github.edtechdevelopment.tutoring.api.command.profile;

public interface TutoringProfileCreationCommands {

    InitialProfilesCreatedResult createInitialProfiles(CreateInitialProfilesCommand command);

    ProfileCreatedResult createTeacherProfile(CreateTeacherProfileCommand command);

    ProfileCreatedResult createStudentProfile(CreateStudentProfileCommand command);
}
