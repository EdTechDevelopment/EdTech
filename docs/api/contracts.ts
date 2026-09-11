// Generated from scheduling.openapi.json, contract v1.0.0. Do not edit manually.
// Date/time, date and decimal money values are JSON strings. IDs and cursors are opaque.

export type UserRole = "TEACHER" | "STUDENT";

export type UserStatus = "PENDING_EMAIL_VERIFICATION" | "ACTIVE";

export type UserResponse = {
  id: string;
  email: string;
  pendingEmail: string | null;
  firstName: string;
  lastName: string;
  roles: Array<UserRole>;
  status: UserStatus;
  emailVerifiedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type UserSummary = {
  id: string;
  firstName: string;
  lastName: string;
};

export type TeacherSummary = {
  id: string;
  firstName: string;
  lastName: string;
};

export type TeacherContactSummary = {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
};

export type StudentSummary = {
  id: string;
  firstName: string;
  lastName: string;
};

export type RegisterRequest = {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  roles: Array<UserRole>;
};

export type VerificationPendingResponse = {
  email: string;
  verificationExpiresAt: string;
};

export type ConfirmEmailRequest = {
  token: string;
};

export type ResendEmailVerificationRequest = {
  email: string;
};

export type LoginRequest = {
  email: string;
  password: string;
};

export type TokenResponse = {
  accessToken: string;
  tokenType: "Bearer";
  expiresInSeconds: number;
};

export type UpdateMeRequest = {
  firstName?: string;
  lastName?: string;
  email?: string;
};

export type SubjectResponse = {
  code: string;
  name: string;
};

export type SubjectListResponse = {
  items: Array<SubjectResponse>;
};

export type TeacherProfileUpsertRequest = {
  subjectCodes: Array<string>;
  description?: string | null;
  education?: string | null;
  experienceYears?: number | null;
  city?: string | null;
  photoUrl?: string | null;
};

export type TeacherProfileResponse = {
  user: UserResponse;
  subjectCodes: Array<string>;
  description: string | null;
  education: string | null;
  experienceYears: number | null;
  city: string | null;
  photoUrl: string | null;
};

export type StudentProfileUpsertRequest = {
  birthDate: string;
  subjectCodes: Array<string>;
  photoUrl?: string | null;
};

export type StudentProfileResponse = {
  user: UserResponse;
  birthDate: string;
  subjectCodes: Array<string>;
  photoUrl: string | null;
};

export type MeResponse = {
  user: UserResponse;
  teacherProfile: TeacherProfileResponse | null;
  studentProfile: StudentProfileResponse | null;
};

export type InvitationStatus = "PENDING" | "ACCEPTED" | "REJECTED" | "EXPIRED";

export type CreateStudentInvitationRequest = {
  email: string;
};

export type StudentInvitationResponse = {
  id: string;
  teacher: TeacherContactSummary;
  studentEmail: string;
  studentUserId: string | null;
  status: InvitationStatus;
  createdAt: string;
  expiresAt: string;
  respondedAt: string | null;
};

export type StudentInvitationPage = {
  items: Array<StudentInvitationResponse>;
  nextCursor: string | null;
};

export type StudentListItem = {
  id: string;
  firstName: string;
  lastName: string;
  profileCompleted: boolean;
  photoUrl: string | null;
  subjectCodes: Array<string>;
};

export type StudentPage = {
  items: Array<StudentListItem>;
  nextCursor: string | null;
};

export type StudentCardProfile = {
  birthDate: string;
  subjectCodes: Array<string>;
  photoUrl: string | null;
};

export type StudentStatistics = {
  totalLessons: number;
  completedLessons: number;
  cancelledLessons: number;
  missedLessons: number;
  lastLessonAt: string | null;
};

export type StudentCardResponse = {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  profile: StudentCardProfile | null;
  statistics: StudentStatistics;
};

export type TeacherContactResponse = {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  profileCompleted: boolean;
  subjectCodes: Array<string>;
  description: string | null;
  photoUrl: string | null;
};

export type TeacherContactPage = {
  items: Array<TeacherContactResponse>;
  nextCursor: string | null;
};

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

export type ErrorCode = "VALIDATION_ERROR" | "UNKNOWN_SUBJECT" | "UNAUTHENTICATED" | "INVALID_CREDENTIALS" | "EMAIL_NOT_VERIFIED" | "INVALID_REFRESH_TOKEN" | "FORBIDDEN" | "NOT_FOUND" | "PROFILE_NOT_FOUND" | "EMAIL_ALREADY_EXISTS" | "INVALID_VERIFICATION_TOKEN" | "INVITATION_ALREADY_PENDING" | "INVITATION_EXPIRED" | "INVALID_INVITATION_STATE" | "ALREADY_LINKED" | "STUDENT_NOT_LINKED" | "LESSON_OVERLAP" | "INVALID_LESSON_STATE" | "RATE_LIMIT_EXCEEDED" | "INTERNAL_ERROR";

export type ApiError = {
  code: ErrorCode;
  message: string;
  fieldErrors: Array<FieldError>;
  requestId: string;
};

export type PageQuery = { cursor?: string; limit?: number };
export type TeacherScheduleQuery = PageQuery & { from: string; to: string; studentUserId?: string; subjectCode?: string; status?: LessonStatus; sortOrder?: "ASC" | "DESC" };
export type StudentScheduleQuery = PageQuery & { from: string; to: string; teacherUserId?: string; status?: LessonStatus; sortOrder?: "ASC" | "DESC" };
