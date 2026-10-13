CREATE TABLE tutoring_subjects
(
    code VARCHAR(32) NOT NULL,
    name TEXT        NOT NULL,

    CONSTRAINT pk_tutoring_subjects PRIMARY KEY (code),
    CONSTRAINT ck_tutoring_subjects_code CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),
    CONSTRAINT ck_tutoring_subjects_name CHECK (name !~ '^[[:space:]]*$' AND name = btrim(name))
);

CREATE UNIQUE INDEX uq_tutoring_subjects_name ON tutoring_subjects (lower(name));

CREATE TABLE tutoring_teacher_profiles
(
    user_id          UUID         NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    display_name     TEXT         NOT NULL,
    contact_email    VARCHAR(254) NOT NULL,
    contact_details  JSONB        NOT NULL,
    photo_url        TEXT,
    description      TEXT,
    education        TEXT,
    experience_years INTEGER,
    city             TEXT,

    CONSTRAINT pk_tutoring_teacher_profiles PRIMARY KEY (user_id),
    CONSTRAINT ck_tutoring_teacher_profiles_name CHECK (display_name !~ '^[[:space:]]*$'),
    CONSTRAINT ck_tutoring_teacher_profiles_email CHECK (
        contact_email <> '' AND contact_email = lower(contact_email) AND contact_email = btrim(contact_email)
    ),
    CONSTRAINT ck_tutoring_teacher_profiles_contacts CHECK (jsonb_typeof(contact_details) = 'array'),
    CONSTRAINT ck_tutoring_teacher_profiles_experience CHECK (experience_years >= 0)
);

CREATE INDEX idx_tutoring_teacher_profiles_search ON tutoring_teacher_profiles (created_at DESC, user_id DESC);

CREATE TABLE tutoring_student_profiles
(
    user_id         UUID         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    display_name    TEXT         NOT NULL,
    contact_email   VARCHAR(254) NOT NULL,
    contact_details JSONB        NOT NULL,
    photo_url       TEXT,

    CONSTRAINT pk_tutoring_student_profiles PRIMARY KEY (user_id),
    CONSTRAINT ck_tutoring_student_profiles_name CHECK (display_name !~ '^[[:space:]]*$'),
    CONSTRAINT ck_tutoring_student_profiles_email CHECK (
        contact_email <> '' AND contact_email = lower(contact_email) AND contact_email = btrim(contact_email)
    ),
    CONSTRAINT ck_tutoring_student_profiles_contacts CHECK (jsonb_typeof(contact_details) = 'array')
);

CREATE INDEX idx_tutoring_student_profiles_search ON tutoring_student_profiles (created_at DESC, user_id DESC);

CREATE TABLE tutoring_teacher_profile_subjects
(
    user_id      UUID        NOT NULL,
    subject_code VARCHAR(32) NOT NULL,

    CONSTRAINT pk_tutoring_teacher_profile_subjects PRIMARY KEY (user_id, subject_code),
    CONSTRAINT fk_tutoring_teacher_profile_subjects_profile FOREIGN KEY (user_id)
        REFERENCES tutoring_teacher_profiles (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_tutoring_teacher_profile_subjects_subject FOREIGN KEY (subject_code)
        REFERENCES tutoring_subjects (code) ON DELETE RESTRICT
);

CREATE INDEX idx_tutoring_teacher_profile_subjects_subject ON tutoring_teacher_profile_subjects (subject_code, user_id);

CREATE TABLE tutoring_student_profile_subjects
(
    user_id      UUID        NOT NULL,
    subject_code VARCHAR(32) NOT NULL,

    CONSTRAINT pk_tutoring_student_profile_subjects PRIMARY KEY (user_id, subject_code),
    CONSTRAINT fk_tutoring_student_profile_subjects_profile FOREIGN KEY (user_id)
        REFERENCES tutoring_student_profiles (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_tutoring_student_profile_subjects_subject FOREIGN KEY (subject_code)
        REFERENCES tutoring_subjects (code) ON DELETE RESTRICT
);

CREATE INDEX idx_tutoring_student_profile_subjects_subject ON tutoring_student_profile_subjects (subject_code, user_id);

CREATE TABLE tutoring_student_invitations
(
    id              UUID         NOT NULL,
    teacher_user_id UUID         NOT NULL,
    student_email   VARCHAR(254) NOT NULL,
    student_user_id UUID,
    attached_at     TIMESTAMPTZ,
    status          VARCHAR(16)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    expires_at      TIMESTAMPTZ  NOT NULL,
    responded_at    TIMESTAMPTZ,

    CONSTRAINT pk_tutoring_student_invitations PRIMARY KEY (id),
    CONSTRAINT fk_tutoring_student_invitations_teacher FOREIGN KEY (teacher_user_id)
        REFERENCES tutoring_teacher_profiles (user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_tutoring_student_invitations_email CHECK (
        student_email <> '' AND student_email = lower(student_email) AND student_email = btrim(student_email)
    ),
    CONSTRAINT ck_tutoring_student_invitations_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED')),
    CONSTRAINT ck_tutoring_student_invitations_expiration CHECK (expires_at > created_at),
    CONSTRAINT ck_tutoring_student_invitations_attachment CHECK (
        (student_user_id IS NULL) = (attached_at IS NULL)
    ),
    CONSTRAINT ck_tutoring_student_invitations_attachment_time CHECK (
        attached_at IS NULL OR (attached_at >= created_at AND attached_at < expires_at)
    ),
    CONSTRAINT ck_tutoring_student_invitations_self CHECK (
        student_user_id IS NULL OR teacher_user_id <> student_user_id
    ),
    CONSTRAINT ck_tutoring_student_invitations_response CHECK (
        (status IN ('ACCEPTED', 'REJECTED') AND student_user_id IS NOT NULL AND responded_at IS NOT NULL
            AND responded_at >= attached_at AND responded_at < expires_at)
        OR (status IN ('PENDING', 'EXPIRED') AND responded_at IS NULL)
    )
);

CREATE UNIQUE INDEX uq_tutoring_student_invitations_pending ON tutoring_student_invitations (teacher_user_id, student_email)
    WHERE status = 'PENDING';
CREATE INDEX idx_tutoring_student_invitations_sent ON tutoring_student_invitations (teacher_user_id, created_at DESC, id DESC);
CREATE INDEX idx_tutoring_student_invitations_incoming ON tutoring_student_invitations (student_user_id, created_at DESC, id DESC)
    WHERE student_user_id IS NOT NULL;
CREATE INDEX idx_tutoring_student_invitations_pending_email ON tutoring_student_invitations (student_email, expires_at)
    WHERE status = 'PENDING';

CREATE TABLE tutoring_teacher_students
(
    teacher_user_id UUID        NOT NULL,
    student_user_id UUID        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_tutoring_teacher_students PRIMARY KEY (teacher_user_id, student_user_id),
    CONSTRAINT fk_tutoring_teacher_students_teacher FOREIGN KEY (teacher_user_id)
        REFERENCES tutoring_teacher_profiles (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_tutoring_teacher_students_student FOREIGN KEY (student_user_id)
        REFERENCES tutoring_student_profiles (user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_tutoring_teacher_students_self CHECK (teacher_user_id <> student_user_id)
);

CREATE INDEX idx_tutoring_teacher_students_teacher ON tutoring_teacher_students (teacher_user_id, created_at DESC, student_user_id DESC);
CREATE INDEX idx_tutoring_teacher_students_student ON tutoring_teacher_students (student_user_id, created_at DESC, teacher_user_id DESC);

CREATE TABLE tutoring_command_operations
(
    operation_id          UUID        NOT NULL,
    user_id               UUID        NOT NULL,
    operation_type        VARCHAR(32) NOT NULL,
    payload_fingerprint   CHAR(64)    NOT NULL,
    result_type           TEXT        NOT NULL,
    result_schema_version INTEGER     NOT NULL,
    result_payload        JSONB,
    created_at            TIMESTAMPTZ NOT NULL,
    completed_at          TIMESTAMPTZ,

    CONSTRAINT pk_tutoring_command_operations PRIMARY KEY (operation_id),
    CONSTRAINT ck_tutoring_command_operations_type CHECK (operation_type IN (
        'CREATE_STUDENT_INVITATION', 'ACCEPT_STUDENT_INVITATION', 'REJECT_STUDENT_INVITATION', 'REMOVE_TEACHER_STUDENT'
    )),
    CONSTRAINT ck_tutoring_command_operations_fingerprint CHECK (payload_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_tutoring_command_operations_result_type CHECK (result_type !~ '^[[:space:]]*$'),
    CONSTRAINT ck_tutoring_command_operations_result_version CHECK (result_schema_version > 0),
    CONSTRAINT ck_tutoring_command_operations_result CHECK (
        (result_payload IS NULL) = (completed_at IS NULL)
    ),
    CONSTRAINT ck_tutoring_command_operations_completion CHECK (completed_at >= created_at)
);

CREATE TABLE tutoring_processed_identity_events
(
    event_id            UUID        NOT NULL,
    event_type          VARCHAR(32) NOT NULL,
    user_id             UUID        NOT NULL,
    payload_fingerprint CHAR(64)    NOT NULL,
    fingerprint_version INTEGER     NOT NULL,
    processed_at        TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_tutoring_processed_identity_events PRIMARY KEY (event_id),
    CONSTRAINT ck_tutoring_processed_identity_events_type CHECK (event_type = 'ACCOUNT_EMAIL_VERIFIED'),
    CONSTRAINT ck_tutoring_processed_identity_events_fingerprint CHECK (payload_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_tutoring_processed_identity_events_version CHECK (fingerprint_version > 0)
);
