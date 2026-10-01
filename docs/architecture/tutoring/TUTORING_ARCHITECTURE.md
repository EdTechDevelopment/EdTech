# Tutoring: полная архитектура модуля

## 1. Статус и назначение

 Пути пакетов приведены относительно `io.github.edtechdevelopment`. Документ описывает требуемое поведение, а не утверждает, что классы, миграции и HTTP-контроллеры уже существуют.

### Текущий статус реализации

На 2026-09-25 в backend у Tutoring есть только `package-info.java`; предметная реализация модуля и его Flyway-таблицы ещё не созданы. Identity и Notifications имеют собственный MVP-код, но не все публичные контракты, необходимые этой архитектуре. До реализации межмодульных workflows текущий account-only вход регистрации не является окончательной регистрацией с учебными профилями.


## 2. Источники истины и канонические решения

Для Tutoring этот документ является единой точкой входа. Подробные аргументы, отдельные сценарии и полные описания классов остаются в `TUTORING_STAGE_2_PUBLIC_API.md`, `TUTORING_STAGE_3_DOMAIN.md`, `TUTORING_STAGE_4_APPLICATION.md` и `TUTORING_STAGE_5_INFRASTRUCTURE_AND_PRESENTATION.md`. При расхождении действует более позднее прямое решение пользователя, зафиксированное здесь. HTTP/OpenAPI и код должны быть синхронизированы до объявления соответствующего сценария реализованным.

| Использовать | Не использовать |
|---|---|
| `TEACHER` и `STUDENT` | `TUTOR` или дополнительные роли без отдельного решения |
| Два независимых `TeacherProfile` / `StudentProfile` | Наследование от `Identity.User` или общий `Profile`-агрегат |
| `Identity.User.birthDate` как источник и локальная копия в каждом профиле | Запрос даты рождения Identity при каждом чтении связанного профиля |
| Публичную дату рождения и учебные контакты активного профиля | Настройку `BirthDateVisibility` или раскрытие неподтверждённого email |
| `contactEmail` профиля отдельно от account email | Автоматическую замену account email профильным адресом |
| Предметы преподавателя как специализацию | Запрет вести урок вне `TeacherProfile.subjectCodes` |
| Приглашение как предложение связи | Автоматическое принятие при отправке или подтверждении email |
| Публичный `tutoring.api` для других модулей | Импорт чужим модулем внутреннего Domain/Application/Persistence Tutoring |

TeacherProfile обязан иметь минимум один предмет. StudentProfile может иметь пустой набор. Оба профиля одного пользователя независимы: их имя, предметы, контактная почта и её подтверждение могут различаться.

## 3. Ответственность и границы

Tutoring владеет:

- справочником учебных предметов;
- учебными профилями преподавателя и ученика, их локальными копиями `birthDate` и профильными контактами;
- запросами подтверждения профильной почты;
- приглашениями ученика;
- подтверждённой парой `TeacherStudent`;
- собственными записями идемпотентности команд и обработки событий Identity;
- чтением self/summary/linked-представлений строго по их аудитории.

Tutoring не владеет аккаунтом, ролями, account email, паролем, JWT или статусом пользователя — это Identity. Он не владеет уроками, расписанием и обработкой будущих уроков — это Scheduling. Он не доставляет SMTP и не хранит durable raw token — это Notifications. Tutoring не добавляет или удаляет роли, не создаёт `Identity.User` и не обращается к таблицам других модулей.

Межмодульный инвариант обеспечивает Workflows:

```text
TEACHER ∈ Identity.User.roles ⇔ TeacherProfile существует
STUDENT ∈ Identity.User.roles ⇔ StudentProfile существует
при регистрации создан хотя бы один заполненный профиль
```

Identity хранит единую `birthDate`. Registration/RoleOnboarding workflow передаёт её в Tutoring при создании профиля. Обычное редактирование профиля дату рождения не меняет. Если когда-либо будет разрешено изменение даты аккаунта, отдельный составной workflow должен синхронизировать обе существующие копии.

## 4. Слои и разрешённые зависимости

```text
tutoring
├── api
├── presentation
├── application
├── domain
└── infrastructure
```

Разрешено:

```text
tutoring.presentation   → tutoring.application.port.in / command / result
tutoring.application    → tutoring.domain, tutoring.api
tutoring.infrastructure → tutoring.application.port.out, tutoring.domain
Scheduling/Workflows    → tutoring.api
tutoring.infrastructure.integration.identity      → identity.api
tutoring.infrastructure.integration.notifications → notifications.api
```

Запрещено:

```text
tutoring.api            -X-> внутренние пакеты Tutoring, Spring, HTTP DTO, jOOQ
tutoring.domain         -X-> Spring, security principal, API других модулей,
                            application, repositories, HTTP, SMTP, jOOQ
tutoring.application    -X-> presentation, infrastructure, чужие таблицы
tutoring.presentation   -X-> domain, infrastructure, repositories
Scheduling/Workflows    -X-> внутренние пакеты или таблицы Tutoring
tutoring               -X-> scheduling.* и внутренние пакеты Identity/Notifications
```

Публичные команды `TutoringRegistrationCommands` и `TutoringRelationshipCommands` доступны только утверждённым workflows; self-view — только `MeQueryFacade`. Это закрепляется архитектурными тестами. Пользовательские сервисы получают `actorUserId` от доверенной границы, не из тела HTTP-запроса.

## 5. Итоговая структура пакетов

Пакеты организованы сначала по предметной области, затем по технической роли. `model` содержит типы соответствующей области, а не один «класс-контейнер». Пустые пакеты не создаются.

```text
tutoring
├── api
│   ├── query
│   │   ├── TutoringProfileQuery
│   │   ├── TutoringSubjectQuery
│   │   └── TutoringRelationshipQuery
│   ├── command
│   │   ├── registration
│   │   │   ├── TutoringRegistrationCommands
│   │   │   ├── CreateInitialProfilesCommand
│   │   │   ├── CreateTeacherProfileCommand
│   │   │   ├── CreateStudentProfileCommand
│   │   │   ├── InitialProfilesCreatedResult
│   │   │   └── ProfileCreatedResult
│   │   └── relationship
│   │       ├── TutoringRelationshipCommands
│   │       ├── RemoveTeacherStudentCommand
│   │       └── TeacherStudentRemovalResult
│   ├── model.profile
│   │   ├── ProfileTypeView
│   │   ├── input        TeacherProfileData, StudentProfileData
│   │   ├── self         TeacherProfileView, StudentProfileView
│   │   ├── publicview   PublicTeacherProfileView, PublicStudentProfileView
│   │   ├── summary      TeacherProfileSummary, StudentProfileSummary
│   │   └── linked       LinkedTeacherProfileView, LinkedStudentProfileView
│   └── exception       ProfileNotFoundException, ProfileAlreadyExistsException,
│                       InvalidProfileDataException, UnknownSubjectsException,
│                       TeacherStudentNotLinkedException, IdempotencyConflictException
├── presentation
│   ├── subject         controller, model.response, mapper
│   ├── profile         controller, model.request/response, mapper
│   │   └── verification controller, model.request/response, mapper
│   ├── invitation      controller, model.request/response, mapper
│   ├── relationship    controller, model.response, mapper
│   └── error           handler, model
├── application
│   ├── subject
│   │   ├── port.in      GetSubjectsUseCase
│   │   ├── port.out     SubjectRepository
│   │   ├── model.result SubjectResult
│   │   ├── service      GetSubjectsService, TutoringSubjectQueryService
│   │   ├── mapper       SubjectResultMapper
│   │   └── exception    InvalidSubjectCodeException, SubjectNotFoundException
│   ├── profile
│   │   ├── port.in      UpdateTeacherProfileUseCase, UpdateStudentProfileUseCase,
│   │   │                GetPublicProfileUseCase, SearchPublicProfilesUseCase
│   │   ├── port.out     TeacherProfileRepository, StudentProfileRepository
│   │   ├── model.command UpdateTeacherProfileCommand, UpdateStudentProfileCommand
│   │   ├── model.query  LinkedProfileBatchQuery
│   │   ├── service      ProfileQueryService, PublicProfileSearchService,
│   │   │                LinkedProfileProjectionService,
│   │   │                TeacherProfileUpdateService, StudentProfileUpdateService,
│   │   │                RegistrationProfileService
│   │   ├── mapper       ProfileViewMapper
│   │   └── verification
│   │       ├── port.in  ChangeProfileEmailUseCase,
│   │       │            RequestProfileEmailConfirmationUseCase,
│   │       │            ConfirmProfileEmailUseCase,
│   │       │            ExpireProfileEmailVerificationsUseCase
│   │       ├── port.out ProfileEmailVerificationRepository,
│   │       │            ProfileVerificationTokenGenerator,
│   │       │            ProfileVerificationTokenHasher,
│   │       │            ProfileVerificationEmailSender,
│   │       │            ProfileVerificationEmailQuota
│   │       ├── model    command, result, notification
│   │       └── service  InitialProfileEmailCoordinator,
│   │                    ProfileEmailVerificationService,
│   │                    ExpiredProfileVerificationService
│   ├── invitation
│   │   ├── port.in      Create/ListSent/ListIncoming/Accept/Reject use cases
│   │   ├── port.out     StudentInvitationRepository,
│   │   │                InvitationCursorCodec, InvitationNotificationSender
│   │   ├── model        command, query, result, notification
│   │   ├── service      Create/ListSent/ListIncoming/Accept/Reject services,
│   │   │                AttachPendingInvitationsService
│   │   ├── mapper       InvitationResultMapper
│   │   └── exception    invitation/cursor exceptions
│   ├── relationship
│   │   ├── port.in      ListTeacherStudentsUseCase, ListStudentTeachersUseCase
│   │   ├── port.out     TeacherStudentRepository, RelationshipCursorCodec
│   │   ├── model        query, result
│   │   ├── service      list, public query and trusted command services
│   │   ├── mapper       RelationshipResultMapper
│   │   └── exception    relationship/cursor exceptions
│   ├── integration.identity
│   │   ├── port.in      HandleAccountEmailVerifiedUseCase
│   │   ├── port.out     IdentityAccountGateway
│   │   ├── model        HandleAccountEmailVerifiedCommand, AccountEmailState
│   │   └── service      HandleAccountEmailVerifiedService
│   ├── idempotency
│   │   ├── port.in      CommandIdempotency
│   │   ├── port.out     CommandOperationRepository,
│   │   │                ProcessedIdentityEventRepository
│   │   ├── model        OperationType, PayloadFingerprint, BeginDecision,
│   │   │                CommandOperation, ProcessedIdentityEvent
│   │   └── service      CommandIdempotencyService,
│   │                    IdentityEventDeduplicationService
│   └── email.port.out   NormalizedEmailLock
├── domain
│   ├── profile
│   │   ├── model        TeacherProfile, StudentProfile, ProfileEmail,
│   │   │                SubjectSelection, ProfileType
│   │   ├── exception    profile invariant exceptions
│   │   └── verification
│   │       ├── model    ProfileEmailVerification, VerificationTokenHash,
│   │       │            ProfileEmailVerificationPurpose
│   │       └── exception
│   ├── subject         model(Subject, SubjectCode), exception
│   ├── invitation      model(StudentInvitation, InvitationEmail,
│   │                   InvitationStatus), exception
│   └── relationship    model(TeacherStudent), exception
└── infrastructure
    ├── subject.persistence              adapter, repository, mapper
    ├── profile.persistence              teacher, student, verification
    ├── invitation.persistence           adapter, repository, mapper
    ├── relationship.persistence         adapter, repository, mapper
    ├── idempotency.persistence          command-operation and event-receipt adapters
    ├── integration.identity             IdentityAccountGatewayAdapter,
    │                                    AccountEmailVerifiedEventListener
    ├── integration.notifications        ProfileVerificationEmailSenderAdapter,
    │                                    InvitationNotificationSenderAdapter
    ├── email.persistence                PostgreSqlNormalizedEmailLock,
    │                                    PostgreSqlProfileVerificationEmailQuota
    ├── profile.verification.scheduler   ExpiredProfileVerificationJob
    ├── security.token                   SecureProfileVerificationTokenGenerator,
    │                                    Sha256ProfileVerificationTokenHasher
    ├── cursor                           InvitationCursorCodecAdapter,
    │                                    RelationshipCursorCodecAdapter
    └── configuration                    Tutoring configuration/properties
```

Дерево фиксирует архитектурные места типов. Имена простых инфраструктурных классов могут быть уточнены при написании кода без изменения портов и поведения. Generated jOOQ records остаются внутренними техническими типами, а не публичной моделью модуля.

## 6. Публичный Java API Tutoring

Это синхронный межмодульный API. Frontend использует HTTP, а не Java-типы `tutoring.api`.

### 6.1 Query API

```java
public interface TutoringProfileQuery {
    Optional<TeacherProfileView> findTeacherProfile(UUID userId);
    Optional<StudentProfileView> findStudentProfile(UUID userId);
    Optional<PublicTeacherProfileView> findPublicTeacherProfile(UUID userId);
    Optional<PublicStudentProfileView> findPublicStudentProfile(UUID userId);
    void requireTeacherProfile(UUID teacherUserId);
    Map<UUID, TeacherProfileSummary> findTeacherSummaries(Set<UUID> ids);
    Map<UUID, StudentProfileSummary> findStudentSummaries(Set<UUID> ids);
    Optional<LinkedStudentProfileView> findLinkedStudentProfile(
        UUID teacherUserId, UUID studentUserId
    );
    Optional<LinkedTeacherProfileView> findLinkedTeacherProfile(
        UUID studentUserId, UUID teacherUserId
    );
}

public interface TutoringSubjectQuery {
    boolean exists(String subjectCode);
    void requireAllExist(Set<String> subjectCodes);
}

public interface TutoringRelationshipQuery {
    boolean areLinked(UUID teacherUserId, UUID studentUserId);
    void requireLinked(UUID teacherUserId, UUID studentUserId);
    void requireAllLinked(UUID teacherUserId, Set<UUID> studentUserIds);
}
```

`findTeacherProfile`/`findStudentProfile` выдают полные self-view только `MeQueryFacade`. Публичные `findPublicTeacherProfile`/`findPublicStudentProfile` и поиск профилей доступны без связи и без login, но только для профилей активных пользователей Identity. Public-view содержит `birthDate`, `displayName`, `contactDetails`, выбранные `subjectCodes`, фото и соответствующие поля преподавателя; `contactEmail` выдаётся лишь после подтверждения. Pending email и account email не выдаются. Summary содержит лишь `userId` и `displayName`; пустой batch summary возвращает пустую карту без SQL. `requireAllExist` и `requireAllLinked` проверяют наборы одним запросом и возвращают все отсутствующие коды/ID. Предмет урока проверяется по справочнику, не по специализации преподавателя; выбранные предметы пользователя читаются из его профиля.

Linked lookup сперва проверяет `TeacherStudent`: отсутствие связи даёт `Optional.empty()` для операции, требующей отношения, но не запрещает отдельное публичное чтение профиля. Есть связь, но нет профиля — ошибка целостности. Linked-view использует те же публичные профильные данные, включая `birthDate` и подтверждённый current contact email; pending и неподтверждённый email не раскрываются.

### 6.2 Доверенные command API

```java
public interface TutoringRegistrationCommands {
    InitialProfilesCreatedResult createInitialProfiles(
        CreateInitialProfilesCommand command
    );
    ProfileCreatedResult createTeacherProfile(CreateTeacherProfileCommand command);
    ProfileCreatedResult createStudentProfile(CreateStudentProfileCommand command);
}

public record CreateInitialProfilesCommand(
    UUID operationId, UUID userId, LocalDate birthDate,
    Optional<TeacherProfileData> teacherProfile,
    Optional<StudentProfileData> studentProfile
) {}

public record CreateTeacherProfileCommand(
    UUID operationId, UUID userId, LocalDate birthDate, TeacherProfileData profile
) {}

public record CreateStudentProfileCommand(
    UUID operationId, UUID userId, LocalDate birthDate, StudentProfileData profile
) {}

public interface TutoringRelationshipCommands {
    TeacherStudentRemovalResult removeTeacherStudent(
        RemoveTeacherStudentCommand command
    );
}

public record RemoveTeacherStudentCommand(
    UUID operationId, UUID teacherUserId, UUID studentUserId
) {}
```

`createInitialProfiles` требует хотя бы одну профильную модель. `RegistrationWorkflow` проверяет точное соответствие выбранных ролей и данных профилей, создаёт Identity.User, затем вызывает Tutoring в одной PostgreSQL-транзакции. `RoleOnboardingWorkflow` после добавления роли создаёт ровно один профиль. Tutoring через публичный Identity API подтверждает наличие соответствующей роли и не добавляет её сам. `removeTeacherStudent` вызывает только `UnlinkStudentWorkflow` после Scheduling guard/обработки уроков. Все четыре метода требуют внешней транзакции (`MANDATORY`).

Результаты команд содержат минимальные ID/типы, а не HTTP DTO или полные профили. Public-типы не наследуют Domain aggregates.

### 6.3 Public models и ошибки

`TeacherProfileData`: `displayName`, `contactEmail`, `contactDetails`, непустые `subjectCodes`, nullable `description`, `education`, `experienceYears`, `city`, `photoUrl`. `StudentProfileData`: те же общие данные без teacher-полей; `subjectCodes` может быть пустым. `userId` и `birthDate` передаются на уровне доверенной команды, raw password и роли в профильные модели не входят.

Self-view каждого профиля содержит его `userId`, `birthDate`, обычные поля, current/pending профильный email и время подтверждения. Public-view активного профиля доступен всем без связи: `userId`, `birthDate`, `displayName`, `contactDetails`, `subjectCodes`, nullable подтверждённый `contactEmail`, nullable `photoUrl`; у преподавателя также публичны `description`, `education`, `experienceYears`, `city`. Teacher/Student summary содержат только `userId`/`displayName`. Linked-view для списка связей несёт эти же публичные профильные данные вместе с контекстом связи; сама связь не служит разрешением на чтение профиля. Account email fallback внутри Tutoring не выполняется.

Public exceptions: `ProfileNotFoundException`, `ProfileAlreadyExistsException`, `InvalidProfileDataException`, `UnknownSubjectsException`, `TeacherStudentNotLinkedException`, `IdempotencyConflictException`. Они не содержат HTTP status. Списочные и пользовательские внутренние ошибки Application переводит на HTTP-границе.

## 7. Presentation Tutoring

Presentation переводит HTTP DTO в Application commands/queries и получает доверенный `actorUserId` из principal. `tutoring.api` не импортирует HTTP DTO. Составной `GET /api/v1/me`, регистрация, добавление второй роли и unlink endpoint принадлежат Workflows/facades, не контроллерам Tutoring.

### 7.1 Endpoint mapping

Префикс: `/api/v1/tutoring`.

| Метод и путь | Доступ | Результат |
|---|---|---|
| `GET /subjects` | Публично | `200` список `{subjectCode,name}` |
| `GET /profiles/teachers`, `GET /profiles/students` | Публично | `200` страница активных профилей с фильтрами предмета и возраста |
| `GET /profiles/teachers/{userId}`, `GET /profiles/students/{userId}` | Публично | `200` публичный профиль активного пользователя; `404`, если недоступен |
| `PUT /profiles/teacher` | Аутентифицированный владелец TeacherProfile | `204`, полная замена обычных полей |
| `PUT /profiles/student` | Аутентифицированный владелец StudentProfile | `204`, пустые subjectCodes допустимы |
| `PUT /profiles/{type}/contact-email` | Владелец профиля | `200` результат смены адреса |
| `POST /profiles/{type}/contact-email/confirmation-requests` | Владелец профиля | `200` результат повторной проверки CURRENT/PENDING |
| `POST /profile-email-confirmations` | Без обязательной авторизации | `204`, raw token только в теле |
| `POST /invitations` | Активный TEACHER с TeacherProfile | `201` `{invitationId,expiresAt}` |
| `GET /invitations/sent` | Владелец TeacherProfile | `200` cursor-страница |
| `GET /invitations/incoming` | Привязанный аутентифицированный пользователь | `200` cursor-страница |
| `POST /invitations/{id}/accept` | Адресат с StudentProfile | `200` минимальный результат |
| `POST /invitations/{id}/reject` | Адресат; StudentProfile не нужен | `200` минимальный результат |
| `GET /relationships/students` | Владелец TeacherProfile | `200` linked-список |
| `GET /relationships/teachers` | Владелец StudentProfile | `200` linked-список |

Публичный поиск принимает необязательные `subjectCode`, `minAge`, `maxAge`, `cursor`, `limit`; возраст вычисляется из `birthDate` на дату запроса по UTC, не хранится отдельным полем. Список содержит только активные профили Identity, сортируется по `createdAt DESC, userId DESC`, имеет формат `{items,nextCursor}` и предел 1…100 (по умолчанию 50). Курсор подписан и привязан к виду профиля, фильтрам и дате расчёта возраста. Предметный фильтр сверяет выбранные предметы именно в профиле; справочник нужен для выбора и проверки допустимости кода. Нельзя показывать ожидающий подтверждения, приостановленный или деактивированный аккаунт. Для страниц нужен пакетный Identity API проверки активности, чтобы избежать отдельного запроса на каждого пользователя и пустых страниц после фильтрации.

`POST /invitations` и оба ответа требуют заголовок `Idempotency-Key: UUID`. Отсутствующий/некорректный ключ — `400`. Запросы не принимают `actorUserId` или клиентский email при ответе. Получение входящих и отказ доступны до student-onboarding; принятие — после появления StudentProfile. Изменяющего `GET` для подтверждения почты или принятия приглашения нет.

### 7.2 DTO и ответы

Teacher PUT содержит `displayName`, `contactDetails`, непустой `subjectCodes`, nullable `description`, `education`, `experienceYears`, `city`, `photoUrl`. Student PUT содержит общие `displayName`/`contactDetails`/`subjectCodes` и nullable `photoUrl`; пустые subjectCodes разрешены. Коллекции не могут быть `null`. Необязательное поле, опущенное в полном PUT или переданное как `null`, очищается. `birthDate` и email-state этим PUT не изменяются.

Email-change DTO содержит только `newEmail`; confirmation-request DTO — `target: CURRENT|PENDING`; token-confirmation DTO — `token` без userId/email. Ответ смены сообщает, завершено ли подтверждение сразу, ожидается Identity или письмо поставлено в очередь, не раскрывая данные чужого профиля.
Поле `state` этого ответа принимает `CONFIRMED`, `AWAITING_ACCOUNT_VERIFICATION` или `EMAIL_QUEUED`. Это состояние запроса, не новая роль и не статус профиля.

Списки приглашений принимают status-фильтр, cursor и limit (по умолчанию 50, диапазон 1…100), возвращают `{items,nextCursor}` без общего total. Отправленный элемент содержит адрес цели, эффективный статус и даты, но не `studentUserId` и не факт регистрации. Входящий элемент содержит имя преподавателя из batch summary. Списки связей возвращают `linkedAt`, публичные данные связанных профилей и cursor. Связь требуется для самих списков и операций с уроками, но не для публичного просмотра профиля.

### 7.3 Ошибки и доступ

Форма ответа совместима с `{code,message,fieldErrors,requestId}`, но Tutoring не импортирует `identity.presentation`. `400`: валидация, cursor, ключ, неизвестные предметы и единая ошибка любого недействительного токена. `401`: отсутствующая обязательная аутентификация. `403`: действие запрещено, включая создание приглашения без роли TEACHER. `404`: собственный отсутствующий профиль или единый ответ для отсутствующего/чужого приглашения. `409`: конфликт приглашения/состояния, отсутствие StudentProfile при принятии, конфликт operationId. `429`: квота профильных писем и безопасный `Retry-After`. `500/503`: нарушение целостности либо техническая недоступность.

Ни response, ни логи ошибок не включают raw token, полный fingerprint/payload и лишние персональные данные. Пустой справочник предметов — внутренняя ошибка `SubjectNotFoundException`, не успешный пустой список.

## 8. Application Tutoring

Application координирует агрегаты, транзакции, repositories и разрешённые чужие API через порты. Domain защищает локальные инварианты; Application проверяет предметы в справочнике, существование пользователя/роли через Identity, наличие профилей/связи, ownership, лимиты, идемпотентность и атомарность нескольких агрегатов. Время берётся из внедрённого `Clock` и явно передаётся в Domain.

### 8.1 Input ports

| Область | Входной сценарий / результат |
|---|---|
| Subject | `GetSubjectsUseCase.getSubjects(): List<SubjectResult>` |
| Profile | `UpdateTeacherProfileUseCase` и `UpdateStudentProfileUseCase` → `void` |
| Profile email | `ChangeProfileEmailUseCase`, `RequestProfileEmailConfirmationUseCase` → `ProfileEmailChangeResult`; `ConfirmProfileEmailUseCase` → `ProfileEmailConfirmationResult` |
| Verification job | `ExpireProfileEmailVerificationsUseCase.expireBatch(batchSize): int` |
| Invitation | Create, ListSent, ListIncoming, Accept, Reject use cases и соответствующие typed results |
| Relationship | ListTeacherStudents и ListStudentTeachers use cases; trusted delete через `TutoringRelationshipCommands` |
| Identity event | `HandleAccountEmailVerifiedUseCase.handle(command): void` |
| Registration | Доверенные методы `TutoringRegistrationCommands` без дублирующего внутреннего port.in |

`GetSubjectsUseCase` не принимает пользователя. Он получает `SubjectRepository.findAll()`, сортирует по `name ASC, subjectCode ASC` и возвращает immutable-список; пустой справочник — внутренняя ошибка. Публичный `TutoringSubjectQuery` выполняет только `exists`/batch `requireAllExist`.

Profile update полностью заменяет обычные поля, проверяет subject codes batch-запросом, загружает собственный профиль `FOR UPDATE` и сохраняет его. Это не PATCH, не меняет `birthDate` и email state, не обещает `expectedVersion` для stale browser form. Self-query через `MeQueryFacade` не перепроверяет роль внутри Tutoring; Facade обнаруживает роль без профиля как нарушение инварианта.

### 8.2 Output ports и ключевые реализации

| Порт | Возможности |
|---|---|
| `SubjectRepository` | `findAll`, `exists`, `findExistingCodes` |
| `TeacherProfileRepository` / `StudentProfileRepository` | `findByUserId`, `findByUserIdForUpdate`, `existsByUserId`, batch summary/linked, явные `insert`/`update` |
| `ProfileEmailVerificationRepository` | hash lookup, `FOR UPDATE`, ещё не терминальные цели, VER-06 batch, явные `insert`/`update` |
| `StudentInvitationRepository` | locked lookup, pending by teacher/email, active pending by email, keyset pages, `insert`/`update`/`updateAll` |
| `TeacherStudentRepository` | pair lookup/lock/exists, batch linked IDs, keyset pages, `insert`/`delete` |
| `CommandOperationRepository` | атомарное reserve/replay/complete по operationId |
| `ProcessedIdentityEventRepository` | атомарный receipt по eventId |
| `IdentityAccountGateway` | состояние account email, поиск по подтверждённому email и разрешённая проверка роли через публичный Identity API |
| `NormalizedEmailLock` | общая транзакционная блокировка нормализованного email |
| `ProfileVerificationEmailQuota` | максимум пять новых писем за rolling 24h по userId |
| Token generator/hasher | криптостойкий raw token и hash для поиска |
| Email senders | атомарный enqueue заявок в Notifications |
| Cursor codecs | проверяемые курсоры invitation/relationship |

`ProfileViewMapper` имеет отдельные self, summary и linked-операции. `RelationshipResultMapper` принимает linked-view, не self-view. Mapper не определяет права доступа и не делает SQL/Identity-вызовов.

### 8.3 Транзакционные сценарии

| Сценарий | Граница |
|---|---|
| Read-only subject/profile/списки | Чтение без скрытой материализации истечения |
| Обычный update профиля | Локальная write-транзакция с блокировкой профиля |
| Initial profile / second role | Внешняя транзакция Workflows; Tutoring command `MANDATORY` |
| Смена/подтверждение профильного email | Локальная write-транзакция: email lock, профиль, verification, quota при письме, Notifications enqueue |
| VER-06 | Отдельная транзакция для каждой ограниченной пачки |
| INV-01 | Локальная write: operationId, email lock, старое истечение, новое приглашение, письмо |
| INV-04/05 | Локальная write: operationId, invitation `FOR UPDATE`; accept также вставляет связь |
| Identity email event | Локальная write: event receipt, email lock, профили/verification/приглашения |
| Unlink | Внешняя транзакция UnlinkStudentWorkflow с Scheduling guard и удалением пары |

## 9. Domain Tutoring

Domain — чистая Java-модель без Spring, SQL, security principal и чужих модулей. Время передаётся аргументом; Domain не вызывает `Instant.now()`. Aggregates не содержат repositories и не создают межмодульные эффекты.

### 9.1 Профили и предметы

`TeacherProfile` и `StudentProfile` — независимые Aggregate Root с ID `userId`. Оба хранят `birthDate: LocalDate`, `displayName`, current `contactEmail`, nullable `pendingContactEmail`, nullable `contactEmailVerifiedAt`, `contactDetails: List<String>`, `SubjectSelection` и nullable `photoUrl`. Teacher дополнительно имеет nullable `description`, `education`, `experienceYears` и `city`. У Student предметов может не быть; Teacher требует минимум один. Имя непустое, email корректен и нормализован, опыт неотрицателен. `SubjectSelection` — неизменяемый набор уникальных `SubjectCode`; `SubjectCode` принадлежит `domain.subject.model`. `Subject` — справочная сущность с кодом и названием.

Операции профилей: `updateDetails`, `changeSubjects`, `changeContactEmail`, `verifyCurrentContactEmail` и `confirmPendingContactEmail`. Операции `changeBirthDateVisibility` нет: настройка видимости даты рождения отменена. Обычное обновление не меняет подтверждение почты. Передача текущего адреса в `changeContactEmail` — полный no-op даже при существующем pending; передача того же pending также не создаёт нового запроса. Другой pending адрес заменяет прежний запрос, старый токен не оживает при возвращении к прошлому адресу. Подтверждение current не очищает pending; подтверждение pending заменяет current, очищает pending и фиксирует время. Домен не проверяет существование Identity.User, роль, предмет в справочнике или право просмотра `birthDate`.

### 9.2 ProfileEmailVerification

Самостоятельный Aggregate Root: `id`, `userId`, `ProfileType`, `targetEmail`, `purpose` (`INITIAL_CONFIRMATION`/`EMAIL_CHANGE`), `tokenHash`, `createdAt`, `expiresAt`, nullable `consumedAt`/`invalidatedAt`/`expiredAt`. Raw token отсутствует. Операции `create`, `isActiveAt(now)`, `consume(now)`, `invalidate(now)`, `expire(now)` защищают одноразовость и терминальное состояние. Токен действует ровно 30 минут; при `now >= expiresAt` он недействителен синхронно, даже если ежедневная задача ещё не установила `expiredAt`. Время жизни — конфигурация Tutoring, не Identity.

### 9.3 StudentInvitation

Самостоятельный Aggregate Root: `id`, `teacherUserId`, фиксированный `studentEmail`, nullable `studentUserId` и `attachedAt`, `status`, `createdAt`, `expiresAt`, nullable `respondedAt`. TTL — 30 × 24 часа. При известном адресате `attachedAt = createdAt`; поздняя `attachStudent(userId, now)` устанавливает оба поля один раз, повтор с тем же пользователем — no-op, переназначение запрещено. Она не создаёт роль, профиль, связь или принятие.

Допустимы только `PENDING → ACCEPTED/REJECTED/EXPIRED`. `accept`/`reject` проверяют адресата, сохранённый `PENDING` и `now < expiresAt`, устанавливают `respondedAt` только при ответе. `effectiveStatusAt` не изменяет состояние; исторический статус cursor-страницы на прежний `asOf` после более позднего ответа вычисляет read-query по `respondedAt`/`expiresAt`. `expire(now)` материализует старый истёкший PENDING перед созданием нового приглашения; отдельной фоновой задачи для всех приглашений в v1 нет.

### 9.4 TeacherStudent

Самостоятельный Aggregate Root с идентичностью направленной пары `teacherUserId + studentUserId` и `createdAt`, без surrogate ID, статуса и вложения в профиль. Запрещает связь с самим собой. Создаётся при принятии приглашения атомарно с `ACCEPTED`. Удаляется физически только после внешнего Scheduling workflow. Domain связи не обращается к урокам.

## 10. Infrastructure Tutoring

Infrastructure реализует output ports Application. Реализации не выходят в публичный API и не определяют бизнес-правила. Пакеты группируются по областям `subject`, `profile`, `verification`, `invitation`, `relationship`, `idempotency`; технические адаптеры — в `integration`, `cursor`, `token`, `job`. jOOQ Record, SQL и Notifications DTO не выходят из адаптеров.

### 10.1 Persistence

- `SubjectRepositoryAdapter` читает справочник и пакетно проверяет коды. Пустой batch не делает SQL; пустой справочник как ошибку интерпретирует Application.
- Отдельные Teacher/Student adapters используют явные `insert` и `update`, без upsert. Профиль и предметы сохраняются атомарно. При update выбор предметов синхронизируется по разнице под блокировкой профиля.
- Self/summary/linked-проекции различны. Batch summary возвращает только ID и имя без N+1. Linked-проекция включает `birthDate` и лишь подтверждённый current email, но не pending. Проверка связи находится в Application до выдачи linked-view. Отсутствие профиля при существующей связи — повреждение данных.
- Verification adapter ищет по hash, затем перечитывает `FOR UPDATE` после получения email lock и проверяет `now < expiresAt`. VER-06 использует ограниченную выборку `FOR UPDATE SKIP LOCKED`.
- Invitation adapter материализует старое истёкшее `PENDING` перед новым. Ответ блокирует приглашение; принятие сохраняет ответ и связь одной транзакцией. Keyset-запрос фильтрует эффективный статус до `limit + 1`.
- Relationship adapter работает с составным ID пары; конфликт вставки не превращается в update. Delete ожидает ровно одну строку и доступен лишь доверенной команде внутри внешнего workflow.
- `CommandOperationRepositoryAdapter` и `ProcessedIdentityEventRepositoryAdapter` находятся в `infrastructure.idempotency.persistence`. Это собственные данные Tutoring, хотя второй адаптер обрабатывает событие Identity.

Неизвестное нарушение CHECK/FK, повреждённый JSON, неподдерживаемый enum или пропажа связанного профиля — ошибка целостности, не бизнес-`not found`.

### 10.2 Интеграция с Identity

`IdentityAccountGatewayAdapter` использует только публичный Java API Identity: состояние account email, поиск пользователя по подтверждённому email и проверку роли при доверенном создании профиля. Ни прямого SQL к Identity, ни импорта `identity.domain` нет. Отсутствие пользователя отличается от недоступности API. Дату рождения для создания профиля передаёт доверенный workflow, а не HTTP-клиент.

Listener `AccountEmailVerifiedEvent` переводит событие в Application-команду с `eventId`, `userId`, `verifiedEmail`, `verifiedAt`. Он не меняет таблицы сам. Application повторно читает актуальное состояние Identity: запоздалое событие не подтверждает уже другой адрес. Для гарантированного retry при сбое Tutoring публикация на стороне Identity должна быть durable outbox; одного in-memory Spring event недостаточно.

### 10.3 Интеграция с Notifications

`ProfileVerificationEmailSenderAdapter` ставит заявку с адресатом, raw token, назначением, `expiresAt` и dedup key `PROFILE_EMAIL_VERIFICATION:{verificationId}`. `InvitationNotificationSenderAdapter` передаёт `studentEmail`, `invitationId`, имя преподавателя, `expiresAt` и ключ `INVITATION_CREATED:{invitationId}`; ID/факт регистрации адресата не передаёт. Адаптеры используют публичный API Notifications, не его хранилище.

Tutoring хранит только hash токена; raw token существует в памяти при вызове Notifications. Notifications формирует ссылку, хранит raw token/URL в своей durable delivery и отправляет SMTP после commit. Первая отправка после `expiresAt` запрещена. Enqueue участвует в общей транзакции: его ошибка откатывает бизнес-изменение, а SMTP-сбой после commit его не откатывает. Существующий verification-only API Notifications требует расширения: отдельное назначение профильного подтверждения, приглашение и dedup key.

### 10.4 Технические адаптеры и задачи

Генератор токена использует не менее 32 случайных байт, URL-safe Base64 без padding; hash — SHA-256 как 64 hex-символа. Raw token не логируется и не сохраняется в Tutoring. Подписанные HMAC URL-safe курсоры включают версию, владельца, направление, фильтр, keyset-позицию; invitation cursor также `asOf`. Подпись и структура проверяются до SQL. Курсор не шифруется, поэтому секретных данных в нём нет; срок жизни курсора v1 не устанавливает.

Application использует общий внедрённый `Clock`; Domain получает время явно. В БД время сохраняется с микросекундной точностью. `ExpiredProfileVerificationJob` ежедневно по UTC вызывает VER-06 пачками по умолчанию по 100, каждая пачка — отдельная транзакция с `SKIP LOCKED`. Истёкший запрос недействителен и до запуска задачи. Фоновой задачи для приглашений в v1 нет.

## 11. PostgreSQL и миграции Tutoring

В v1 ровно десять таблиц Tutoring. Миграции принадлежат Tutoring и не изменяют чужие схемы. Межмодульных FK к Identity, Scheduling или Notifications нет; внутренние FK Tutoring допустимы. Таблицы профилей и receipts хранят UUID чужих пользователей как внешние идентификаторы без SQL-зависимости от Identity.

| Таблица | Ключевые поля и ограничения |
|---|---|
| `tutoring_subjects` | `code VARCHAR(32) PK`, `name TEXT NOT NULL`; uppercase ASCII код `^[A-Z][A-Z0-9_]*$`, непустое название. Начальные записи — миграцией; пользовательского CRUD нет. |
| `tutoring_teacher_profiles` | `user_id UUID PK`, неизменяемый `created_at`, общие профильные поля и nullable `description`, `education`, `experience_years >= 0`, `city`. FK на Identity нет. |
| `tutoring_student_profiles` | `user_id UUID PK`, неизменяемый `created_at` и общие профильные поля. FK на Identity нет. |
| `tutoring_teacher_profile_subjects` | `(user_id,subject_code) PK`; FK на собственный TeacherProfile `ON DELETE CASCADE` и Subject `ON DELETE RESTRICT`. |
| `tutoring_student_profile_subjects` | Аналогичные PK/FK для StudentProfile; ноль строк допустим. |
| `tutoring_profile_email_verifications` | UUID PK, `(user_id,profile_type,purpose,target_email,token_hash,created_at,expires_at,consumed_at,invalidated_at,expired_at)`; unique hash; не более одного терминального времени; `expires_at > created_at`. |
| `tutoring_student_invitations` | UUID PK, teacher ID, email, nullable student ID/attachedAt, status и даты; внутренний FK на TeacherProfile без cascade; FK на StudentProfile/Identity нет. |
| `tutoring_teacher_students` | `(teacher_user_id,student_user_id) PK`, `created_at`; ID различны; внутренние FK на оба профиля без cascade. |
| `tutoring_command_operations` | `operation_id UUID PK`, user ID, тип, fingerprint, typed versioned result JSON и даты; незавершённое состояние не коммитится. |
| `tutoring_processed_identity_events` | `event_id UUID PK`, тип события, user ID, versioned fingerprint и `processed_at`; raw email не хранится. |

Общие профильные столбцы: `birth_date DATE NOT NULL`, `display_name TEXT NOT NULL`, `contact_email VARCHAR(254) NOT NULL`, nullable `pending_contact_email VARCHAR(254)`, nullable `contact_email_verified_at TIMESTAMPTZ`, `contact_details JSONB NOT NULL` как массив строк, nullable `photo_url TEXT`. БД проверяет непустое имя, нормализованность email, отличие pending от current и форму JSON; Domain дополнительно проверяет инварианты. Email профиля не уникален: оба профиля и разные пользователи могут иметь одинаковый контакт. BirthDate — копия значения Identity на момент создания; обычный update её не меняет.
Публичный поиск использует неизменяемый `created_at` и `user_id` для страниц; индексы по этой паре, `birth_date` и обратный индекс `(subject_code,user_id)` на выбранных предметах поддерживают фильтры. Статус ACTIVE проверяется в Identity, не копируется в таблицы Tutoring.

До включения составной регистрации существующие локальные аккаунты могут иметь роли без профилей. Миграция не создаёт профили автоматически из `firstName`/`lastName`: обязательных `contactDetails`, выбранных предметов и профильного email так достоверно не получить. Для локальных тестовых данных допустимо явное пересоздание после резервной копии; для реальных аккаунтов нужен отдельный заполненный onboarding/migration-сценарий до включения инварианта и публичного поиска.

Verification имеет частичный unique `(user_id,profile_type,purpose)`, пока `consumed_at`, `invalidated_at` и `expired_at` равны `NULL`: запросы current и pending могут сосуществовать. Индексы покрывают batch-истечение по `expires_at` и квоту по `(user_id,created_at)`. Старый истёкший/заменяемый запрос переводится в терминальное состояние в той же транзакции до нового. Строки сохраняются минимум до конца 24-часового окна квоты.

Invitation требует согласованной пары nullable `student_user_id`/`attached_at`, `expires_at > created_at` и соответствия `responded_at` статусу. Частичный unique `(teacher_user_id,student_email) WHERE status='PENDING'` предотвращает второй сохранённый PENDING; перед повторной отправкой старое истечение материализуется. Индексы покрывают отправленный/входящий keyset и поиск действующих PENDING по email. У relationship есть индексы по обеим сторонам для `created_at DESC, other_user_id DESC`.

Operation result хранится с `result_type` и положительной `result_schema_version`; даже `void` представлен явным JSON-значением. Результат существует только у завершённой операции. Event fingerprint строится из канонических `eventType`, `userId`, нормализованного email и `verifiedAt`, имеет положительную версию. Для command operations и event receipts политика retention не согласована; автоматического удаления в v1 нет.

## 12. Внешние Workflows и фасады

Tutoring не владеет всей транзакцией регистрации, ролями Identity и расписанием. Эти процессы координируются отдельными верхнеуровневыми workflow/facade, вызывающими публичные API модулей.

| Координатор | Последовательность и граница |
|---|---|
| `RegistrationWorkflow` | Валидирует выбранные роли (минимум STUDENT или TEACHER) и соответствующие заполненные профильные данные; в общей транзакции создаёт Identity.User и ровно требуемые Tutoring-профили с его `birthDate`. Ошибка любого шага откатывает оба модуля. |
| `RoleOnboardingWorkflow` | Для второй роли получает данные нового профиля и подтверждённый Identity `birthDate`, добавляет роль в Identity и создаёт ровно один профиль в той же транзакции. Первый профиль не меняется. |
| `UnlinkStudentWorkflow` | В общей транзакции применяет Scheduling guard, обрабатывает уже существующие уроки по политике Scheduling и только затем вызывает `TutoringRelationshipCommands.removeTeacherStudent`. Создание нового урока использует совместимый guard/lock. |
| `MeQueryFacade` | Объединяет Identity и Tutoring self-данные для `/me`; отсутствие профиля при имеющейся роли трактует как нарушение инварианта, не как нормальное «пусто». |
| `StudentCardQueryFacade` | Собирает карточку ученика из публичных данных Tutoring; статистику его уроков показывает только преподавателю с подтверждённой связью через отдельный доступ Scheduling. BirthDate берёт из профиля Tutoring. |

Доверенные команды Tutoring требуют активной общей PostgreSQL-транзакции (`MANDATORY`) и не доступны прямым HTTP-контроллером. Для сквозной атомарности модули используют один DataSource/transaction manager. Если в будущем граница транзакции изменится, эти workflow нужно перепроектировать как распределённые процессы, а не молча сохранить прежние гарантии. `operationId` приходит из доверенного workflow либо из проверенного HTTP `Idempotency-Key`.

## 13. Жизненный цикл профильной почты и события Identity

Account email Identity и contact email каждого из двух профилей независимы. На создании профиль хранит current contact email, но до подтверждения этот адрес не виден другим. Обычный PUT не меняет email-state. Изменение контактного адреса не меняет account email и не вызывает изменение роли.

1. `changeContactEmail(newEmail)` с текущим адресом — полный no-op, даже если pending уже задан. Тот же pending — no-op без новой заявки. Другой адрес становится pending, старый запрос аннулируется; его токен не оживает при последующем возврате к этому адресу.
2. Application сверяет цель с подтверждённым текущим account email через Identity API. Совпадение подтверждает current или pending сразу, без письма. Если account email совпадает, но ещё не подтверждён, профиль ожидает Identity-событие, тоже без отдельного письма.
3. Для иного адреса создаётся verification с TTL 30 минут и после проверки квоты атомарно ставится письмо в Notifications. Квота — максимум пять **новых писем** за скользящие 24 часа на `userId` суммарно по обеим ролям; считаются и впоследствии использованные/аннулированные запросы.
4. Публичное подтверждение принимает только raw token в теле POST. Application вычисляет hash, находит цель, берёт блокировки, повторно проверяет актуальность профиля/цели и `now < expiresAt`, затем однократно consume. Подтверждение current не очищает pending; подтверждение pending заменяет current и очищает pending.
5. Ежедневный VER-06 только материализует `EXPIRED` для уже недействительных verification. Он не требуется для отказа в использовании истёкшего токена.
6. Identity event сначала дедуплицируется по `eventId`, затем Application повторно проверяет актуальный подтверждённый account email. Если адрес совпал с current/pending профиля, завершается подтверждение. Тем же событием привязываются действующие приглашения на адрес; принятия и создания StudentProfile не происходит. Устаревшее событие фиксируется как обработанное no-op.

Отсутствие Identity API или ошибка Notifications enqueue до commit откатывают текущую операцию. Повторная доставка события и повторный POST токена не создают повторного перехода. На внешней границе любой недействительный токен даёт одинаковую безопасную ошибку.

## 14. Приглашения, страницы и связь

`INV-01`: учитель с активной TEACHER-ролью и TeacherProfile указывает email. Application нормализует его, запрещает самоприглашение, проверяет отсутствие подтверждённой связи и действующего PENDING. Известного пользователя ищет лишь по **подтверждённому** account email Identity; неизвестный адрес допустим. При создании известный recipient привязывается сразу; для неизвестного ID остаётся пустым. Новое приглашение живёт ровно 30 суток, уведомление ставится в очередь атомарно. Создание не требует StudentProfile и не позволяет учителю выбрать recipient ID.

Привязка позже происходит при актуальном подтверждении account email в Identity. `attachStudent` устанавливает ID и `attachedAt`, но не принимает приглашение и не назначает роль. Она идемпотентна для того же ID; переназначение другому пользователю запрещено. Истёкшие приглашения не привязываются. Отдельной периодической INV-06 нет: эффективный статус вычисляется при чтении, а сохранённый `EXPIRED` материализуется при повторном создании.

Отправленные/входящие страницы сортируются по `created_at DESC, id DESC`. Первый запрос фиксирует `asOf`, который подписанный cursor переносит на следующие страницы. Фильтр эффективного статуса применяется **до** `limit + 1`; исторический статус на `asOf` использует `respondedAt` и `expiresAt`, не позднейшие изменения. Входящие требуют `student_user_id = actorUserId` и `attached_at <= asOf`. Отправленный элемент не раскрывает recipient ID/факт регистрации; входящий обогащается именем учителя batch summary. Limit — 50 по умолчанию, 1…100; нет total count.

`INV-04` (accept) и `INV-05` (reject) проверяют привязанного адресата, `PENDING` и строгую границу `now < expiresAt`. Reject доступен до появления StudentProfile, accept — только после него. На ответном шаге TeacherProfile отправителя не перечитывается: действительное приглашение уже установило отправителя. Accept меняет статус и создаёт одну `TeacherStudent` пару атомарно. Конкурентные ответы сериализуются блокировкой и PK; второй действительный переход невозможен. Отсутствующее и чужое приглашение имеют одинаковый внешний `404`.

Relationship-списки сортируются по `created_at DESC, other_user_id DESC` и дают подписанный cursor без `asOf` — живой список, не исторический snapshot. Проверка пары ограничивает доступ к списку связей и урокам; данные самого активного профиля, включая дату рождения и подтверждённые контакты, публичны. Удаление связи не входит в эти read use cases и происходит только по `UnlinkStudentWorkflow`.

## 15. Транзакции, блокировки и идемпотентность

`NormalizedEmailLock` использует транзакционную PostgreSQL advisory lock по нормализованному email. Одинаковый механизм обязателен для профильного email, INV-01 и Identity event, чтобы они не обходили друг друга. Коллизия hash разных email допускает лишь лишнюю сериализацию. Отдельная область advisory-ключей защищает квоту писем по userId.

Порядок в write-сценарии: reserve command/event ID → email lock → строка профиля/приглашения → verification → quota lock (если создаётся письмо) → Notifications enqueue. Предварительный lookup токена по hash допустим без блокировки, но после email lock все условия перечитываются `FOR UPDATE`. Для INV-04/05 блокируется приглашение; для accept в той же транзакции вставляется пара. VER-06 работает малыми транзакциями и `SKIP LOCKED`. Чтение страниц не материализует истечение и не блокирует чужие записи.

`operationId` обозначает одну бизнес-команду, `operationType` и канонический `payloadFingerprint` — её содержание. Адаптер резервирует ID через `INSERT ... ON CONFLICT DO NOTHING`; конкурентный запрос ждёт commit/rollback. Точное совпадение завершённой команды возвращает сохранённый typed result, несоответствие выдаёт `IdempotencyConflictException`. Завершение с `completedAt` и result происходит в той же транзакции, что бизнес-изменение. Незавершённая запись не может остаться после commit; `complete` повторно выполнить нельзя. Отдельных публичных сценариев «найти/разрешить/завершить» вызывающий модуль не оркестрирует.

Для Identity event аналогичный receipt keyed by `eventId`: точный повтор — no-op; тот же ID с иным fingerprint — конфликт/сигнал повреждения. Проверка Identity может признать событие устаревшим; после успешной проверки такой receipt завершается no-op. Ошибка актуальной проверки откатывает receipt, чтобы доставка могла повториться. Payload декодируется только по известным result types/schema versions; произвольных Java class names в JSON нет.

Уникальные ограничения БД остаются последней защитой гонок. Предсказуемое нарушение partial unique или PK переводится в предметный конфликт; неизвестное нарушение схемы не маскируется под пользовательскую ошибку.

## 16. Безопасность и приватность

- `actorUserId` берётся только из доверенного principal или доверенного workflow. HTTP-тело не может задавать владельца профиля, отправителя приглашения или ID адресата ответа.
- Self-view получает только владелец. Публичный профиль активного пользователя доступен всем без связи: дата рождения, имя профиля, описание, выбранные предметы, контактные данные и подтверждённый current profile email. Summary остаётся минимальным для пакетных внутренних запросов. Настройки видимости даты нет.
- Неподтверждённый current email и любой pending доступны лишь владельцу. Account email не служит fallback для публичной карточки. Наличие связи раскрывается только её участникам; публичность профиля не открывает уроки и приглашения.
- Поиск адресата приглашения не позволяет отправителю узнать, существует ли такой пользователь: ответ/список не возвращает recipient ID, а уведомление не включает этот факт.
- Raw token не попадает в Tutoring-таблицы, URL query логов, ответы, exception messages или audit payload. Подтверждение — POST с token в теле. Hash токена не является общедоступным идентификатором.
- Cursor привязан к владельцу, направлению, фильтру и версии; HMAC проверяется до запросов. Cursor не шифруется и не содержит секретов.
- Domain и API не импортируют Spring Security. Tutoring Infrastructure не читает/не обновляет чужие таблицы; взаимодействие с Identity/Notifications/Scheduling идёт через утверждённые публичные интерфейсы и внешние workflows.

## 17. Матрица реализаций и зависимостей

| Контракт / сценарий | Application | Infrastructure / вызывающий |
|---|---|---|
| `TutoringSubjectQuery`, `GetSubjectsUseCase` | Subject query service | `SubjectRepositoryAdapter` |
| `TutoringTeacherProfileQuery` / `TutoringStudentProfileQuery` | Profile query services; self/summary/linked mapper | Раздельные profile repository adapters |
| Profile PUT use cases | Teacher/Student profile services | Profile + Subject repositories |
| `TutoringRegistrationCommands` | Trusted profile creation service с MANDATORY-транзакцией | `RegistrationWorkflow` / `RoleOnboardingWorkflow` |
| Email change/request/confirm | Profile email service | Profile/verification repos, Identity gateway, token/quota/Notifications |
| `ExpireProfileEmailVerificationsUseCase` | VER-06 service | Verification repo и daily job |
| Invitation create/list/respond | Invitation services | Invitation repo, Identity gateway, email lock, Notifications, idempotency |
| Relationship list | Relationship query service | Pair repo и batch linked-profile projections |
| `TutoringRelationshipCommands` | Trusted unlink command, MANDATORY | `UnlinkStudentWorkflow` и Scheduling guard |
| Identity email event | Event handler service | Identity listener/gateway, event receipt, email lock |
| HTTP routes | Presentation controllers/advice | Application input ports; principal adapter |

Матрица показывает направленность: внешний workflow → `tutoring.api` → Application; Application → собственные output ports; Infrastructure → чужой **публичный** API. Уровни API/Domain не зависят от реализации адаптеров.

## 18. Архитектурные проверки

Автоматические архитектурные тесты должны запретить:

1. Импорты Spring, HTTP DTO, jOOQ, repository, SMTP, security principal и чужого Domain в `tutoring.domain`.
2. Импорты `tutoring.infrastructure` или `tutoring.presentation` из `tutoring.api`.
3. SQL/ORM-доступ Tutoring к таблицам Identity, Scheduling и Notifications, межмодульные FK и прямые зависимости от их Infrastructure.
4. Вызов Tutoring trusted commands из прямых HTTP-контроллеров вместо утверждённых workflows.
5. Публичные API, возвращающие Domain Aggregate Root, persistence Record или полный профиль чужого пользователя.
6. Место хранения raw verification token в Tutoring и прямую отправку SMTP в локальной бизнес-транзакции.
7. Скрытые изменения в read-only запросах и независимое удаление `TeacherStudent` вне `UnlinkStudentWorkflow`.

Дополнительно проверяется предметная структура пакетов: `model` находится внутри каждого поддомена; общего глобального `domain.model` / `application.model` с разнотипными данными нет.

## 19. Обязательные тесты реализации

| Область | Проверка |
|---|---|
| Регистрация/роли | Одна или две роли → все обязательные профили заполнены с копией birthDate; ошибка Identity/Tutoring вызывает общий rollback; добавление второй роли не меняет первый профиль. |
| Профиль/предметы | Teacher без предметов отклонён, Student с пустым набором допустим; update не меняет email/birthDate; nullable experience/city работают; предмет урока не ограничен специализацией. |
| Профильная почта | Current сохраняется до подтверждения pending; same current/pending — no-op; смена pending аннулирует токен; old/used/invalidated/expired token отвергается; подтверждение current не сбрасывает pending. |
| Identity event | Актуальный verified account email подтверждает совпадающую цель и привязывает приглашения; устаревшее событие no-op; дубли и изменённый payload с тем же eventId обрабатываются правильно. |
| Квота/доставка | Не более пяти новых писем на userId за rolling 24h по двум ролям; concurrent request не превышает лимит; ошибка enqueue откатывает всё; SMTP после commit не меняет результат; доставка впервые после expiresAt запрещена. |
| Приглашения | Unknown email допустим; known email сразу attached; позднее событие привязывает без принятия; самоприглашение, второй active PENDING и переназначение отвергаются. |
| Ответ/связь | Reject до student-onboarding допустим, accept без StudentProfile запрещён; `now == expiresAt` запрещает оба ответа; accept атомарно создаёт ровно одну пару; конкурентный accept не дублирует её. |
| Списки/приватность | Фильтр статуса до limit; устойчивые keyset страницы при одинаковом `createdAt`; `attachedAt > asOf` не появляется позднее; публичный профиль активного пользователя виден без связи, включая birthDate и подтверждённые контакты; pending email и наличие чужих связей не раскрываются. |
| Unlink | Scheduling guard и обработка уроков перед delete; сбой откатывает guard/уроки/пару; конкурентное создание урока использует совместимый guard. |
| Идемпотентность | Точный replay возвращает прежний typed result; иной payload с тем же ID — конфликт; незавершённая операция не коммитится; две конкурирующие команды/события не создают дубликат. |
| VER-06 | Синхронный отказ просроченному токену до job; ежедневная batch-задача материализует истечение; два экземпляра `SKIP LOCKED` не обрабатывают запись дважды. |

Проверки ограничений, блокировок и конкуренции выполняются интеграционными тестами с реальным PostgreSQL, не только in-memory БД. Тесты времени используют фиксируемый `Clock`. Это критерии **будущей** реализации, а не отчёт о пройденных тестах.

## 20. Порядок реализации и критерии готовности

Рекомендуемый порядок: использовать согласованные целевые HTTP/Java-контракты; реализовать необходимые Identity/Notifications/Scheduling API и общую транзакцию; добавить миграции/справочник; реализовать Domain и `tutoring.api`; затем Application subject/profile/public search/idempotency, profile email и Identity event, invitation/relationship; после этого adapters, jobs, HTTP Presentation, workflows/facades и тесты. OpenAPI обновлён как целевой контракт до реализации и сверяется с каждым готовым сценарием. Каждый вертикальный сценарий можно проверять раньше завершения всех соседних областей.

Tutoring можно считать реализованным только когда:

- роли/профили создаются атомарно при регистрации и добавлении второй роли, а отсутствие профиля при роли не является нормальным состоянием;
- контактная почта, приглашения, связь и отвязка выполняют утверждённые переходы и не нарушают приватность;
- PostgreSQL ограничения, конкуренция, идемпотентность, outbox/Notifications и job проверены интеграционно;
- публичный Java API и HTTP-контракты доступны своим потребителям, все внешние вызовы идут через API, не чужую persistence;
- архитектурные и сквозные тесты проходят, а документация/OpenAPI соответствуют реализации.

На дату этой спецификации Tutoring **спроектирован, но не реализован**: в дереве Java-пакета есть только `package-info.java`; таблиц и рабочих сценариев Tutoring в репозитории нет. Эта документация не является заявлением о готовности к эксплуатации.

## 21. Связанные материалы и вопросы синхронизации

Базовые решения подробно зафиксированы в `tutoring-stage-1-boundaries-and-use-cases.md`, `TUTORING_STAGE_2_PUBLIC_API.md`, `TUTORING_STAGE_3_DOMAIN.md`, `TUTORING_STAGE_4_APPLICATION.md`, `TUTORING_STAGE_5_INFRASTRUCTURE_AND_PRESENTATION.md` и едином `TUTORING_APPLICATION_SCENARIOS.md` в той же папке. При расхождении применяются более поздние согласованные решения этого итогового документа: Student может не выбирать предметы; `birthDate` копируется в оба профиля и публична в активном профиле; настройки видимости нет. Подтверждённые профильные контакты и описание также доступны без связи.

Соседние материалы и контракты синхронизированы на уровне целевой документации; при реализации остаются такие зависимости:

1. `Identity.User.birthDate` остаётся первоисточником, локальная копия Tutoring публична только в ACTIVE профиле. Identity должен добавить дату в хранение/регистрацию и предоставить доверенное чтение для onboarding второй роли.
2. `AccountEmailVerifiedEvent.occurredAt` означает фактический момент подтверждения. Для надёжной доставки нужен Identity outbox и receipt Tutoring по `eventId`; перед применением события Tutoring повторно проверяет актуальный подтверждённый адрес.
3. Identity нужны публичные query/role API и пакетная проверка ACTIVE для поиска; Notifications — профильный verification и invitation delivery с dedup key; Scheduling — совместимый guard/обработка уроков для unlink.
4. Политика retention операций/receipts и произвольные дополнительные поля без отдельного решения не вводятся. Старые локальные аккаунты без профилей требуют явного перехода данных, описанного выше.

Кодовые модули Identity, Notifications и Scheduling в рамках этой синхронизации документации не изменялись.
