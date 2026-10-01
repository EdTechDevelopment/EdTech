// Synchronized with scheduling.openapi.json, contract v1.2.0.
// Date/time/date values are JSON strings. IDs and cursors are opaque.

export type UserRole = "TEACHER" | "STUDENT";
export type UserStatus = "PENDING_EMAIL_VERIFICATION" | "ACTIVE" | "SUSPENDED" | "DEACTIVATED";

export type UserResponse = {
  id: string;
  email: string;
  pendingEmail: string | null;
  firstName: string;
  lastName: string;
  birthDate: string;
  roles: Array<UserRole>;
  status: UserStatus;
  emailVerifiedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type UserSummary = { id: string; firstName: string; lastName: string };
export type TeacherSummary = { id: string; displayName: string };
export type TeacherContactSummary = { id: string; displayName: string; contactEmail: string | null };
export type StudentSummary = { id: string; displayName: string };

export type TeacherProfileInput = {
  displayName: string;
  contactEmail: string;
  contactDetails: Array<string>;
  subjectCodes: Array<string>;
  description?: string | null;
  education?: string | null;
  experienceYears?: number | null;
  city?: string | null;
  photoUrl?: string | null;
};

export type StudentProfileInput = {
  displayName: string;
  contactEmail: string;
  contactDetails: Array<string>;
  subjectCodes: Array<string>;
  photoUrl?: string | null;
};

type RegistrationAccount = {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  birthDate: string;
};
export type TeacherRegistrationRequest = RegistrationAccount & {
  roles: ["TEACHER"];
  teacherProfile: TeacherProfileInput;
  studentProfile?: never;
};
export type StudentRegistrationRequest = RegistrationAccount & {
  roles: ["STUDENT"];
  teacherProfile?: never;
  studentProfile: StudentProfileInput;
};
export type TeacherStudentRegistrationRequest = RegistrationAccount & {
  roles: ["TEACHER", "STUDENT"] | ["STUDENT", "TEACHER"];
  teacherProfile: TeacherProfileInput;
  studentProfile: StudentProfileInput;
};
export type RegisterRequest = TeacherRegistrationRequest | StudentRegistrationRequest | TeacherStudentRegistrationRequest;

export type VerificationPendingResponse = { email: string; verificationExpiresAt: string };
export type ConfirmEmailRequest = { token: string };
export type ResendEmailVerificationRequest = { email: string };
export type LoginRequest = { email: string; password: string };
export type TokenResponse = { accessToken: string; tokenType: "Bearer"; expiresInSeconds: number };
export type UpdateMeRequest = { firstName?: string; lastName?: string; email?: string };

export type SubjectResponse = { subjectCode: string; name: string };
export type GetSubjectsResponse = Array<SubjectResponse>;

export type TeacherProfileUpdateRequest = {
  displayName: string;
  contactDetails: Array<string>;
  subjectCodes: Array<string>;
  description?: string | null;
  education?: string | null;
  experienceYears?: number | null;
  city?: string | null;
  photoUrl?: string | null;
};
export type StudentProfileUpdateRequest = {
  displayName: string;
  contactDetails: Array<string>;
  subjectCodes: Array<string>;
  photoUrl?: string | null;
};

export type PublicStudentProfileResponse = {
  userId: string;
  birthDate: string;
  displayName: string;
  contactEmail: string | null;
  contactDetails: Array<string>;
  subjectCodes: Array<string>;
  photoUrl: string | null;
};
export type PublicTeacherProfileResponse = PublicStudentProfileResponse & {
  description: string | null;
  education: string | null;
  experienceYears: number | null;
  city: string | null;
};
export type PublicTeacherProfilePage = { items: Array<PublicTeacherProfileResponse>; nextCursor: string | null };
export type PublicStudentProfilePage = { items: Array<PublicStudentProfileResponse>; nextCursor: string | null };

export type TeacherProfileResponse = PublicTeacherProfileResponse & {
  contactEmail: string;
  pendingContactEmail: string | null;
  contactEmailVerifiedAt: string | null;
};
export type StudentProfileResponse = PublicStudentProfileResponse & {
  contactEmail: string;
  pendingContactEmail: string | null;
  contactEmailVerifiedAt: string | null;
};
export type MeResponse = {
  user: UserResponse;
  teacherProfile: TeacherProfileResponse | null;
  studentProfile: StudentProfileResponse | null;
};

export type ProfileEmailChangeRequest = { newEmail: string };
export type ProfileEmailConfirmationRequest = { target: "CURRENT" | "PENDING" };
export type ProfileEmailTokenRequest = { token: string };
export type ProfileEmailRequestResult = {
  state: "CONFIRMED" | "AWAITING_ACCOUNT_VERIFICATION" | "EMAIL_QUEUED";
};

export type InvitationStatus = "PENDING" | "ACCEPTED" | "REJECTED" | "EXPIRED";
export type CreateStudentInvitationRequest = { studentEmail: string };
export type CreatedInvitationResponse = { invitationId: string; expiresAt: string };
export type SentInvitation = {
  invitationId: string;
  studentEmail: string;
  status: InvitationStatus;
  createdAt: string;
  expiresAt: string;
  respondedAt: string | null;
};
export type IncomingInvitation = {
  invitationId: string;
  teacher: TeacherSummary;
  status: InvitationStatus;
  createdAt: string;
  expiresAt: string;
  respondedAt: string | null;
};
export type SentInvitationPage = { items: Array<SentInvitation>; nextCursor: string | null };
export type IncomingInvitationPage = { items: Array<IncomingInvitation>; nextCursor: string | null };
export type InvitationDecisionResult = { invitationId: string; status: "ACCEPTED" | "REJECTED" };

export type LinkedStudentItem = { linkedAt: string; profile: PublicStudentProfileResponse };
export type LinkedTeacherItem = { linkedAt: string; profile: PublicTeacherProfileResponse };
export type LinkedStudentsPage = { items: Array<LinkedStudentItem>; nextCursor: string | null };
export type LinkedTeachersPage = { items: Array<LinkedTeacherItem>; nextCursor: string | null };

export type StudentCardProfile = PublicStudentProfileResponse;
export type StudentStatistics = {
  totalLessons: number;
  completedLessons: number;
  cancelledLessons: number;
  missedLessons: number;
  lastLessonAt: string | null;
};
export type StudentCardResponse = { id: string; profile: StudentCardProfile; statistics: StudentStatistics };

export type LessonFormat = "INDIVIDUAL" | "GROUP";

export type LocationType = "ONLINE" | "OFFLINE";

export type LessonStatus = "SCHEDULED" | "COMPLETED" | "CANCELLED" | "MISSED";

export type LessonCancelReason = "ILLNESS" | "FAMILY" | "NOT_READY" | "OTHER" | "STUDENT_UNLINKED";

export type LessonParticipantSummary = StudentSummary;

export type CreateLessonRequest = ({
  studentUserIds: Array<string>;
  subjectCode: string;
  format: "INDIVIDUAL";
  locationType: "ONLINE";
  price?: string | null;
  startAt: string;
  endAt: string;
  meetingUrl: string;
}) | ({
  studentUserIds: Array<string>;
  subjectCode: string;
  format: "INDIVIDUAL";
  locationType: "ONLINE";
  price?: string | null;
  startAt: string;
  durationMinutes: 30 | 45 | 60 | 90 | 120;
  meetingUrl: string;
}) | ({
  studentUserIds: Array<string>;
  subjectCode: string;
  format: "INDIVIDUAL";
  locationType: "OFFLINE";
  price?: string | null;
  startAt: string;
  endAt: string;
  offlineAddress: string;
}) | ({
  studentUserIds: Array<string>;
  subjectCode: string;
  format: "INDIVIDUAL";
  locationType: "OFFLINE";
  price?: string | null;
  startAt: string;
  durationMinutes: 30 | 45 | 60 | 90 | 120;
  offlineAddress: string;
}) | ({
  studentUserIds: Array<string>;
  subjectCode: string;
  format: "GROUP";
  locationType: "ONLINE";
  price?: string | null;
  startAt: string;
  endAt: string;
  meetingUrl: string;
}) | ({
  studentUserIds: Array<string>;
  subjectCode: string;
  format: "GROUP";
  locationType: "ONLINE";
  price?: string | null;
  startAt: string;
  durationMinutes: 30 | 45 | 60 | 90 | 120;
  meetingUrl: string;
}) | ({
  studentUserIds: Array<string>;
  subjectCode: string;
  format: "GROUP";
  locationType: "OFFLINE";
  price?: string | null;
  startAt: string;
  endAt: string;
  offlineAddress: string;
}) | ({
  studentUserIds: Array<string>;
  subjectCode: string;
  format: "GROUP";
  locationType: "OFFLINE";
  price?: string | null;
  startAt: string;
  durationMinutes: 30 | 45 | 60 | 90 | 120;
  offlineAddress: string;
});

export type UpdateLessonRequest = {
  studentUserIds?: Array<string>;
  subjectCode?: string;
  format?: LessonFormat;
  locationType?: LocationType;
  meetingUrl?: string | null;
  offlineAddress?: string | null;
  price?: string | null;
  startAt?: string;
  endAt?: string;
  durationMinutes?: 30 | 45 | 60 | 90 | 120;
};

export type TeacherLessonResponse = ({
  id: string;
  teacher: TeacherSummary;
  participants: Array<LessonParticipantSummary>;
  subjectCode: string;
  format: "INDIVIDUAL";
  locationType: "ONLINE";
  meetingUrl: string | null;
  offlineAddress: string | null;
  price: string | null;
  startAt: string;
  endAt: string;
  durationMinutes: 30 | 45 | 60 | 90 | 120;
  status: LessonStatus;
  cancelReason: LessonCancelReason | null;
  cancelComment: string | null;
  cancelledByUserId: string | null;
  cancelledAt: string | null;
}) | ({
  id: string;
  teacher: TeacherSummary;
  participants: Array<LessonParticipantSummary>;
  subjectCode: string;
  format: "INDIVIDUAL";
  locationType: "OFFLINE";
  meetingUrl: string | null;
  offlineAddress: string | null;
  price: string | null;
  startAt: string;
  endAt: string;
  durationMinutes: 30 | 45 | 60 | 90 | 120;
  status: LessonStatus;
  cancelReason: LessonCancelReason | null;
  cancelComment: string | null;
  cancelledByUserId: string | null;
  cancelledAt: string | null;
}) | ({
  id: string;
  teacher: TeacherSummary;
  participants: Array<LessonParticipantSummary>;
  subjectCode: string;
  format: "GROUP";
  locationType: "ONLINE";
  meetingUrl: string | null;
  offlineAddress: string | null;
  price: string | null;
  startAt: string;
  endAt: string;
  durationMinutes: 30 | 45 | 60 | 90 | 120;
  status: LessonStatus;
  cancelReason: LessonCancelReason | null;
  cancelComment: string | null;
  cancelledByUserId: string | null;
  cancelledAt: string | null;
}) | ({
  id: string;
  teacher: TeacherSummary;
  participants: Array<LessonParticipantSummary>;
  subjectCode: string;
  format: "GROUP";
  locationType: "OFFLINE";
  meetingUrl: string | null;
  offlineAddress: string | null;
  price: string | null;
  startAt: string;
  endAt: string;
  durationMinutes: 30 | 45 | 60 | 90 | 120;
  status: LessonStatus;
  cancelReason: LessonCancelReason | null;
  cancelComment: string | null;
  cancelledByUserId: string | null;
  cancelledAt: string | null;
});

export type StudentLessonResponse = ({
  id: string;
  teacher: TeacherContactSummary;
  participants: Array<LessonParticipantSummary>;
  subjectCode: string;
  format: "INDIVIDUAL";
  locationType: "ONLINE";
  meetingUrl: string | null;
  offlineAddress: string | null;
  startAt: string;
  endAt: string;
  durationMinutes: 30 | 45 | 60 | 90 | 120;
  status: LessonStatus;
  cancelReason: LessonCancelReason | null;
  cancelComment: string | null;
  cancelledAt: string | null;
}) | ({
  id: string;
  teacher: TeacherContactSummary;
  participants: Array<LessonParticipantSummary>;
  subjectCode: string;
  format: "INDIVIDUAL";
  locationType: "OFFLINE";
  meetingUrl: string | null;
  offlineAddress: string | null;
  startAt: string;
  endAt: string;
  durationMinutes: 30 | 45 | 60 | 90 | 120;
  status: LessonStatus;
  cancelReason: LessonCancelReason | null;
  cancelComment: string | null;
  cancelledAt: string | null;
}) | ({
  id: string;
  teacher: TeacherContactSummary;
  participants: Array<LessonParticipantSummary>;
  subjectCode: string;
  format: "GROUP";
  locationType: "ONLINE";
  meetingUrl: string | null;
  offlineAddress: string | null;
  startAt: string;
  endAt: string;
  durationMinutes: 30 | 45 | 60 | 90 | 120;
  status: LessonStatus;
  cancelReason: LessonCancelReason | null;
  cancelComment: string | null;
  cancelledAt: string | null;
}) | ({
  id: string;
  teacher: TeacherContactSummary;
  participants: Array<LessonParticipantSummary>;
  subjectCode: string;
  format: "GROUP";
  locationType: "OFFLINE";
  meetingUrl: string | null;
  offlineAddress: string | null;
  startAt: string;
  endAt: string;
  durationMinutes: 30 | 45 | 60 | 90 | 120;
  status: LessonStatus;
  cancelReason: LessonCancelReason | null;
  cancelComment: string | null;
  cancelledAt: string | null;
});

export type TeacherLessonPage = {
  items: Array<TeacherLessonResponse>;
  nextCursor: string | null;
};

export type StudentLessonPage = {
  items: Array<StudentLessonResponse>;
  nextCursor: string | null;
};

export type CancelLessonRequest = {
  reason: "ILLNESS" | "FAMILY" | "NOT_READY" | "OTHER";
  comment?: string;
};

export type SetLessonStatusRequest = {
  status: "COMPLETED" | "MISSED";
};

export type FieldError = {
  field: string;
  code: string;
  message: string;
};

export type ErrorCode = "VALIDATION_ERROR" | "UNKNOWN_SUBJECT" | "UNAUTHENTICATED" | "INVALID_CREDENTIALS" | "EMAIL_NOT_VERIFIED" | "INVALID_REFRESH_TOKEN" | "FORBIDDEN" | "NOT_FOUND" | "PROFILE_NOT_FOUND" | "EMAIL_ALREADY_EXISTS" | "INVALID_VERIFICATION_TOKEN" | "INVITATION_ALREADY_PENDING" | "INVITATION_EXPIRED" | "INVALID_INVITATION_STATE" | "ALREADY_LINKED" | "STUDENT_NOT_LINKED" | "LESSON_OVERLAP" | "INVALID_LESSON_STATE" | "RATE_LIMIT_EXCEEDED" | "ROLE_ALREADY_ASSIGNED" | "ROLE_PROFILE_MISMATCH" | "IDEMPOTENCY_CONFLICT" | "PROFILE_EMAIL_VERIFICATION_REQUIRED" | "INVALID_PROFILE_EMAIL_VERIFICATION_TOKEN" | "INTERNAL_ERROR";

export type ApiError = {
  code: ErrorCode;
  message: string;
  fieldErrors: Array<FieldError>;
  requestId: string;
};

export type PageQuery = { cursor?: string; limit?: number };
export type TeacherScheduleQuery = PageQuery & { from: string; to: string; studentUserId?: string; subjectCode?: string; status?: LessonStatus; sortOrder?: "ASC" | "DESC" };
export type StudentScheduleQuery = PageQuery & { from: string; to: string; teacherUserId?: string; status?: LessonStatus; sortOrder?: "ASC" | "DESC" };
