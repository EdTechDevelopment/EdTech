# Tutoring — этап 4. Итоговая спецификация Application

Статус: утверждён пользователем 2026-09-24. Документ объединяет согласованные сценарии и последующие уточнения обсуждения. Это архитектурная спецификация, а не Java-реализация.
Синхронизация от 2026-09-24: для приглашений учтён `attachedAt`, а логический `save` портов хранения уточнён до явных `insert`/`update`. Полный Persistence/Infrastructure/Presentation описан в `TUTORING_STAGE_5_INFRASTRUCTURE_AND_PRESENTATION.md`.

## 1. Граница Application

`tutoring.application` координирует агрегаты Tutoring, транзакции, порты хранения, публичный Java API Tutoring и интеграции через разрешённые API других модулей. Domain защищает локальные инварианты агрегатов; Application проверяет существование пользователя, роли, предметов и профилей, наличие связи, право раскрытия данных, идемпотентность и согласованность нескольких агрегатов.

Identity владеет аккаунтом, ролями, account email и первоначальной `birthDate`. Tutoring владеет двумя независимыми профилями, их копиями `birthDate`, профильной почтой, предметами, приглашениями и связями. Scheduling владеет уроками; Notifications — очередью доставки писем, включая raw token до отправки. Application Tutoring не принимает HTTP DTO, не читает чужие таблицы, не обращается к `SecurityContext`, SMTP, jOOQ или Spring-событиям напрямую.

Время получает сервис через `Clock` и явно передаёт в Domain. Публичные межмодульные интерфейсы остаются в `tutoring.api`; внутренний `port.in` их не дублирует. Пользовательские use case получают `actorUserId` только от доверенной presentation/workflow-границы. Ни один сервис Tutoring не добавляет роль Identity.

## 2. Структура по предметным областям

Пакет верхнего уровня сначала обозначает область, затем роль класса. Пустые пакеты не создаются; `model` содержит только внутренние данные Application, не Domain aggregate и не HTTP DTO.

```text
tutoring.application
├── subject
│   ├── port.in                  GetSubjectsUseCase
│   ├── port.out                 SubjectRepository
│   ├── model.result             SubjectResult
│   ├── service                  GetSubjectsService, TutoringSubjectQueryService
│   ├── mapper                   SubjectResultMapper
│   └── exception                InvalidSubjectCodeException, SubjectNotFoundException
├── profile
│   ├── port.in                  UpdateTeacherProfileUseCase, UpdateStudentProfileUseCase
│   ├── port.out                 TeacherProfileRepository, StudentProfileRepository
│   ├── model.command            UpdateTeacherProfileCommand, UpdateStudentProfileCommand
│   ├── model.query              LinkedProfileBatchQuery
│   ├── service                  ProfileQueryService, LinkedProfileProjectionService,
│   │                            TeacherProfileUpdateService, StudentProfileUpdateService,
│   │                            RegistrationProfileService
│   ├── mapper                   ProfileViewMapper
│   └── verification
│       ├── port.in              ChangeProfileEmailUseCase,
│       │                        RequestProfileEmailConfirmationUseCase,
│       │                        ConfirmProfileEmailUseCase,
│       │                        ExpireProfileEmailVerificationsUseCase
│       ├── port.out             ProfileEmailVerificationRepository,
│       │                        ProfileVerificationTokenGenerator,
│       │                        ProfileVerificationTokenHasher,
│       │                        ProfileVerificationEmailSender,
│       │                        ProfileVerificationEmailQuota
│       ├── model.command        ChangeProfileEmailCommand,
│       │                        RequestProfileEmailConfirmationCommand,
│       │                        ConfirmProfileEmailCommand, ProfileEmailTarget
│       ├── model.result         ProfileEmailChangeResult, ProfileEmailConfirmationResult
│       ├── model.notification   ProfileVerificationEmailRequest
│       ├── service              InitialProfileEmailCoordinator,
│       │                        ProfileEmailVerificationService,
│       │                        ExpiredProfileVerificationService
│       └── exception            InvalidProfileVerificationTokenException,
│                                ProfileEmailRateLimitException
├── invitation
│   ├── port.in                  CreateStudentInvitationUseCase,
│   │                            ListSentInvitationsUseCase,
│   │                            ListIncomingInvitationsUseCase,
│   │                            AcceptStudentInvitationUseCase,
│   │                            RejectStudentInvitationUseCase
│   ├── port.out                 StudentInvitationRepository,
│   │                            InvitationCursorCodec, InvitationNotificationSender
│   ├── model.command            CreateStudentInvitationCommand,
│   │                            AcceptStudentInvitationCommand,
│   │                            RejectStudentInvitationCommand
│   ├── model.query              SentInvitationsQuery, IncomingInvitationsQuery,
│   │                            InvitationPageCursor, InvitationPageRow
│   ├── model.result             InvitationCreatedResult, SentInvitationPageResult,
│   │                            IncomingInvitationPageResult,
│   │                            InvitationAcceptedResult, InvitationRejectedResult
│   ├── model.notification       StudentInvitationNotification
│   ├── service                  CreateStudentInvitationService,
│   │                            ListSentInvitationsService,
│   │                            ListIncomingInvitationsService,
│   │                            AcceptStudentInvitationService,
│   │                            RejectStudentInvitationService,
│   │                            AttachPendingInvitationsService
│   ├── mapper                   InvitationResultMapper
│   └── exception                InvitationUnavailableException,
│                                InvitationCreationConflictException,
│                                InvitationResponseConflictException,
│                                InvalidInvitationCursorException
├── relationship
│   ├── port.in                  ListTeacherStudentsUseCase, ListStudentTeachersUseCase
│   ├── port.out                 TeacherStudentRepository, RelationshipCursorCodec
│   ├── model.query              ListTeacherStudentsQuery,
│   │                            ListStudentTeachersQuery, RelationshipPageCursor
│   ├── model.result             StudentRelationshipResult,
│   │                            StudentRelationshipPageResult,
│   │                            TeacherRelationshipResult,
│   │                            TeacherRelationshipPageResult
│   ├── service                  ListTeacherStudentsService,
│   │                            ListStudentTeachersService,
│   │                            TutoringRelationshipQueryService,
│   │                            TutoringRelationshipCommandService
│   ├── mapper                   RelationshipResultMapper
│   └── exception                InvalidRelationshipCursorException,
│                                TeacherStudentAlreadyLinkedException,
│                                RelationshipWriteConflictException
├── integration.identity
│   ├── port.in                  HandleAccountEmailVerifiedUseCase
│   ├── port.out                 IdentityAccountGateway
│   ├── model.command            HandleAccountEmailVerifiedCommand
│   ├── model.result             AccountEmailState, VerifiedAccountReference
│   ├── service                  HandleAccountEmailVerifiedService
│   └── exception                InvalidIdentityEventException,
│                                IdentityUserConsistencyException
├── idempotency
│   ├── port.in                  CommandIdempotency
│   ├── port.out                 CommandOperationRepository,
│   │                            ProcessedIdentityEventRepository
│   ├── model                    OperationType, PayloadFingerprint,
│   │                            BeginDecision<R>, CommandOperation,
│   │                            ProcessedIdentityEvent
│   └── service                  CommandIdempotencyService,
│                                IdentityEventDeduplicationService
└── email
    └── port.out                 NormalizedEmailLock
```

`TeacherStudentNotLinkedException`, `ProfileNotFoundException`, `ProfileAlreadyExistsException`, `InvalidProfileDataException`, `UnknownSubjectsException` и `IdempotencyConflictException` остаются в `tutoring.api.exception`: Application не создаёт их дубликаты. Типы self/summary/linked-view и регистрационные команды также остаются в `tutoring.api`. Имена новых внутренних классов в дереве фиксируют намерение; сигнатуры портов ниже являются логическими контрактами без привязки к Spring или SQL.

Общая `NormalizedEmailLock` живёт отдельно от `profile.verification` и `invitation`. Её метод `lock(normalizedEmail)` требует активной write-транзакции; блокировка удерживается до её завершения. Единый порядок захвата блокировок для нескольких адресов и реализация определяются Infrastructure.

### 2.1 Логические сигнатуры входных сценариев

Обозначение `execute(command)` ниже задаёт контракт Application, не HTTP endpoint. `actorUserId` всегда доверенный; nullable-поля и проверки входа описаны в сценариях соответствующих блоков.

| Интерфейс | Метод и результат |
|---|---|
| `GetSubjectsUseCase` | `List<SubjectResult> getSubjects()` |
| `UpdateTeacherProfileUseCase` | `void execute(UpdateTeacherProfileCommand)` |
| `UpdateStudentProfileUseCase` | `void execute(UpdateStudentProfileCommand)` |
| `ChangeProfileEmailUseCase` | `ProfileEmailChangeResult execute(ChangeProfileEmailCommand)` |
| `RequestProfileEmailConfirmationUseCase` | `ProfileEmailChangeResult execute(RequestProfileEmailConfirmationCommand)` |
| `ConfirmProfileEmailUseCase` | `ProfileEmailConfirmationResult execute(ConfirmProfileEmailCommand)` |
| `ExpireProfileEmailVerificationsUseCase` | `int expireBatch(int batchSize)`; scheduler повторяет пачки до пустой |
| `CreateStudentInvitationUseCase` | `InvitationCreatedResult execute(CreateStudentInvitationCommand)` |
| `ListSentInvitationsUseCase` | `SentInvitationPageResult execute(SentInvitationsQuery)` |
| `ListIncomingInvitationsUseCase` | `IncomingInvitationPageResult execute(IncomingInvitationsQuery)` |
| `AcceptStudentInvitationUseCase` | `InvitationAcceptedResult execute(AcceptStudentInvitationCommand)` |
| `RejectStudentInvitationUseCase` | `InvitationRejectedResult execute(RejectStudentInvitationCommand)` |
| `ListTeacherStudentsUseCase` | `StudentRelationshipPageResult execute(ListTeacherStudentsQuery)` |
| `ListStudentTeachersUseCase` | `TeacherRelationshipPageResult execute(ListStudentTeachersQuery)` |
| `HandleAccountEmailVerifiedUseCase` | `void handle(HandleAccountEmailVerifiedCommand)` |

`ChangeProfileEmailCommand` содержит `(actorUserId, profileType, newEmail)`. `RequestProfileEmailConfirmationCommand` содержит `(actorUserId, profileType, target)`, где внутренний `ProfileEmailTarget` равен `CURRENT` или `PENDING`; это устраняет неоднозначность, когда одновременно ожидают подтверждения оба адреса. `ConfirmProfileEmailCommand` содержит только raw token; результат не возвращает email или userId посетителю ссылки. Создание/ответ на приглашение содержит `operationId`, доверенный actor и email либо `invitationId`. Query списка содержит доверенный actor, фильтр статуса, cursor и limit. Точные имена полей result-моделей должны совпадать с описанным в разделах 4–7 составом, без скрытых account/role данных.

## 3. Subject

`GetSubjectsUseCase.getSubjects(): List<SubjectResult>` не принимает параметров и не проверяет пользователя. Сервис вызывает `SubjectRepository.findAll()`, преобразует `Subject` в `SubjectResult(subjectCode, name)`, сортирует по `name ASC, subjectCode ASC` и возвращает неизменяемый список. Пустой справочник — утверждённая внутренняя ошибка `SubjectNotFoundException`.

Публичный `TutoringSubjectQuery.exists(String)` возвращает `false` для корректного, но отсутствующего кода. `requireAllExist(Set<String>)` одним batch-запросом определяет все отсутствующие коды и выдаёт публичную `UnknownSubjectsException`. `InvalidSubjectCodeException`/`SubjectNotFoundException` в документе Subject являются внутренними ошибками; прямой выброс `SubjectNotFoundException` из публичного `requireAllExist` исключён. Специализация преподавателя не ограничивает предмет урока.

## 4. Profile

### 4.1 Чтение и раскрытие данных

`ProfileQueryService` реализует `TutoringProfileQuery`. `findTeacherProfile(userId)` и `findStudentProfile(userId)` возвращают полный self-view или `Optional.empty()`, но доступны для показа только через `MeQueryFacade`, сверяющий `principal.userId == userId`. Self-view содержит `birthDate`, текущий и pending email и состояние подтверждения. Он не содержит роль, пароль или account email.

`findTeacherSummaries(Set<UUID>)` и `findStudentSummaries(Set<UUID>)` делают один batch-запрос и возвращают `Map<UUID, Summary>` только для найденных профилей. Пустой набор даёт пустую карту без БД; это уточнение позднего сценария PROF-03/04 имеет приоритет над общим правилом этапа 2 о пустом batch. Summary содержит только `userId` и `displayName`.

`LinkedProfileProjectionService` пакетно собирает `LinkedStudentProfileView`/`LinkedTeacherProfileView` только для ID, которые `relationship` уже получил из подтверждённых связей. Linked-view содержит дату рождения из профиля и только подтверждённый current `contactEmail`; pending и неподтверждённый email не передаются. Отсутствующий профиль при существующей связи — ошибка целостности, не неполная карточка. Для одиночного `findLinked*Profile` сервис relationship сначала проверяет связь и лишь затем запрашивает профиль; без связи возвращает `Optional.empty()`.

`ProfileViewMapper` имеет разные операции для self, summary и linked. `RelationshipResultMapper` принимает только linked-view, не self-view. Mapper не обращается к репозиториям, Identity или Notifications и не принимает решение о наличии права на просмотр.

### 4.2 Обычные изменения

`UpdateTeacherProfileUseCase` полностью заменяет обычные поля `displayName`, `contactDetails`, `subjectCodes`, `description`, `education`, `experienceYears`, `city`, `photoUrl`; `UpdateStudentProfileUseCase` — `displayName`, `contactDetails`, `subjectCodes`, `photoUrl`. Это не PATCH. Сервис проверяет коды предметов пакетно, загружает собственный профиль с блокировкой, вызывает `updateDetails` и `changeSubjects`, сохраняет агрегат. У преподавателя предметов минимум один; у ученика допустим пустой набор, при котором справочник не запрашивается. Результат `void`, `operationId` не нужен.

`birthDate`, current/pending email, время подтверждения и `userId` обычные update-команды не меняют. Блокировка сериализует записи, но защита от stale browser form через `expectedVersion` отдельно не обещана; это решение Persistence/Presentation.

### 4.3 Создание профилей

`RegistrationProfileService` реализует публичные `createInitialProfiles`, `createTeacherProfile` и `createStudentProfile`. Каждая команда проверяет/резервирует `operationId`, валидирует профильные данные, пакетно проверяет предметы, требует отсутствие целевого профиля и наличие уже созданной роли через публичный Identity API, затем создаёт профиль с переданной Identity `birthDate`. Идемпотентность, профиль, запросы подтверждения и очередь Notifications фиксируются в одной внешней транзакции.

`createInitialProfiles` требует хотя бы один из двух `Optional`; при двух ролях создаёт оба профиля атомарно. Внешний `RegistrationWorkflow` создаёт Identity.User и согласует выбранные роли с данными профилей; Tutoring проверяет лишь существование соответствующих ролей. Внешний `RoleOnboardingWorkflow` сначала добавляет вторую роль через Identity и передаёт существующую `birthDate` в одну типизированную команду Tutoring. Ошибка Tutoring откатывает и создание пользователя/роли во внешнем workflow. У двух профилей одного пользователя почта и предметы независимы.

Первоначальный `contactEmail` сохраняется как current, неподтверждённый; pending отсутствует. Для каждого создаваемого профиля `InitialProfileEmailCoordinator` применяет правила раздела 5. Публичный результат содержит идентификаторы созданных профилей, но не HTTP DTO.

## 5. Profile verification

### 5.1 Состояния и операции

Профиль хранит current, pending и `contactEmailVerifiedAt`; `ProfileEmailVerification` — отдельный агрегат с `id`, `userId`, `profileType`, `targetEmail`, `purpose`, `tokenHash`, `createdAt`, `expiresAt`, `consumedAt?`, `invalidatedAt?` и согласованным дополнением `expiredAt?` для материализации фоновой задачей. Raw token в агрегате и таблицах Tutoring не хранится. Доменный метод `expire(now)` отмечает запрос истёкшим только при `now >= expiresAt` и отсутствии более раннего терминального состояния. Независимо от фоновой задачи `isActiveAt(now)` всегда проверяет время синхронно.

Токен действует 30 минут (`expiresAt = createdAt + 30 минут`). Это отдельная конфигурация Tutoring, не TTL Identity. У заявки Notifications срок отправки не позднее `expiresAt`. Notifications уже сохраняет raw token в своей durable delivery-записи; Tutoring хранит только хеш. Конкретный URL ссылки принадлежит Presentation/Notifications.

### 5.2 Первоначальный адрес

После создания профиля Application получает состояние account email один раз для команды:

1. Совпадающий подтверждённый account email — сразу подтвердить current без письма Tutoring.
2. Совпадающий неподтверждённый account email — ждать событие Identity, не создавать токен и не отправлять второе письмо.
3. Другой адрес — создать одноразовый verification и поставить письмо Tutoring в Notifications в той же транзакции.

Если два профиля ждут одно подтверждение Identity, событие может подтвердить current email обоих.

### 5.3 Смена, повторная проверка и токен

`ChangeProfileEmailUseCase` принимает доверенный `actorUserId`, тип профиля и новый адрес. Передача текущего адреса ничего не меняет, включая существующий pending. Передача уже установленного pending также не заменяет verification и не инициирует переотправку. Другой адрес становится pending; прежний активный verification для смены адреса аннулируется. Возврат позднее к старому pending не восстанавливает старый токен.

Если новый pending совпадает с подтверждённым account email — он становится current сразу, pending очищается, прежний verification для этой цели аннулируется и фиксируется время подтверждения. Если совпадает с неподтверждённым account email — остаётся pending в ожидании события Identity, без письма Tutoring. Если не совпадает — создаётся новый verification и заявка на письмо. Подтверждение текущего адреса не очищает pending.

`RequestProfileEmailConfirmationUseCase` — аутентифицированная повторная проверка current либо pending адреса. Он заново читает Identity: если адрес теперь подтверждённый account email, завершает подтверждение немедленно; если тот же адрес ещё ожидает Identity, остаётся ожидание; если account email уже другой, создаёт письмо Tutoring. Пока для той же цели существует активный токен, второе письмо не создаётся; после истечения можно создать новый запрос. Этот use case покрывает ранее обсуждённое «переотправить/проверить», отдельного слепого resend нет.

`ConfirmProfileEmailUseCase` получает raw token без обязательного login, хеширует его, загружает verification с блокировкой и профиль, проверяет активность, purpose и актуальность target. `INITIAL_CONFIRMATION` подтверждает только совпадающий current; `EMAIL_CHANGE` продвигает только совпадающий pending в current, очищает pending и устанавливает время подтверждения. Verification помечается consumed в той же транзакции. Неизвестный, истёкший, использованный, аннулированный или устаревший токен даёт одну внешнюю ошибку `InvalidProfileVerificationTokenException` без раскрытия состояния адреса.

Новые письма Tutoring ограничены: не более пяти на `userId` за скользящие 24 часа суммарно по обоим профилям. `ProfileVerificationEmailQuota` резервирует слот атомарно в той же транзакции; ожидание Identity и немедленное подтверждение лимит не расходуют. Ошибка enqueue откатывает профиль, verification, резервирование квоты и идемпотентность. SMTP после commit выполняет Notifications и не откатывает Tutoring.

### 5.4 VER-06

Ежедневная фоновая задача вызывает `ExpireProfileEmailVerificationsUseCase` и пакетно материализует истёкшие неиспользованные запросы через `expire(now)`. Каждая ограниченная пачка — отдельная транзакция. Задача не меняет профильный current/pending email и не отправляет письма. Даже если задача задержалась, применение токена при `now >= expiresAt` запрещено синхронной проверкой.

## 6. Invitation

### 6.1 Создание и доставка

`CreateStudentInvitationUseCase` принимает `operationId`, доверенный `teacherUserId` и email адресата. Identity/auth-граница проверяет активность и роль преподавателя; Tutoring требует его `TeacherProfile`, но не перепроверяет роль. После idempotency begin сервис нормализует email, берёт общую email-блокировку, через Identity сравнивает его с account email преподавателя и ищет адресата только по подтверждённому account email. Самоприглашение запрещено, в том числе при известном userId.

Для пары преподаватель + нормализованный email допускается не более одного действующего приглашения. Старый сохранённый `PENDING` с `now >= expiresAt` материализуется через `expire(now)` в этой транзакции перед созданием нового. Для известного адресата проверяется отсутствие `TeacherStudent`, затем новый агрегат создаётся с `studentUserId` и `attachedAt = createdAt`; для неизвестного оба поля остаются пустыми. Роль/профиль STUDENT при этом не требуются.

В той же транзакции сохраняются приглашение, результат идемпотентности и заявка Notifications с ключом `INVITATION_CREATED:{invitationId}`. Payload содержит `invitationId`, фиксированный `studentEmail`, `teacherDisplayName`, `expiresAt`; не содержит `studentUserId` или признака наличия аккаунта. Ответ отправителю — только `invitationId` и `expiresAt`. SMTP выполняется после commit; после `expiresAt` первоначальная отправка не производится. Открытие ссылки не означает принятие.

### 6.2 Списки

`ListSentInvitationsUseCase` доступен владельцу TeacherProfile. Возвращает адрес, эффективный статус и даты, но не `studentUserId` и не факт регистрации. `ListIncomingInvitationsUseCase` выбирает только приглашения с `studentUserId == actorUserId`; для просмотра не требует роли STUDENT и StudentProfile. Имена преподавателей получает одним batch-вызовом `findTeacherSummaries`; отсутствие профиля отправителя при сохранённом приглашении — ошибка целостности.

Оба списка read-only, имеют фильтр по эффективному статусу, непрозрачный cursor и `limit` по умолчанию 50 (диапазон 1…100). Первый запрос закрепляет `asOf`; следующие используют его из курсора, привязанного к владельцу, направлению и фильтру. Сортировка `createdAt DESC, invitationId DESC`; репозиторий применяет фильтр статуса **до** `limit + 1`. Входящий список дополнительно требует `attachedAt <= asOf`, чтобы приглашение, привязанное позднее первой страницы, не появилось посреди просмотра. Для статуса на `asOf` учитываются `respondedAt`, `expiresAt` и сохранённое терминальное состояние: ответ после `asOf` не изменяет статус старой страницы. Чтение не вызывает `expire()`.

### 6.3 Ответ

`AcceptStudentInvitationUseCase` и `RejectStudentInvitationUseCase` принимают `operationId`, `invitationId` и доверенный actor ID; клиентский email не передаётся. После replay-проверки приглашение загружается с блокировкой. Отсутствующее и чужое приглашение дают одинаковый внешний результат; ownership проверяется **до** состояния. Затем требуются `PENDING` и `now < expiresAt`.

Для принятия дополнительно требуется StudentProfile адресата и отсутствие существующей пары. `StudentInvitation.accept(actor, now)` и `TeacherStudent.create(teacher, student, now)` фиксируются атомарно вместе с идемпотентным результатом. Профиль и роль преподавателя повторно не проверяются: TeacherProfile был обязателен при создании, удаление профиля в v1 не предусмотрено. Отказ вызывает только `reject(actor, now)` и не меняет связь; он доступен до student-onboarding. Новая операция над терминальным/истёкшим приглашением отклоняется, точный replay возвращает прежний результат. Уникальная пара в БД защищает от конкурентных дублей.

### 6.4 Привязка по событию и истечение

`AttachPendingInvitationsService` вызывается обработчиком подтверждённого account email. Под общей email-блокировкой он пакетно загружает действующие `PENDING` на адрес с блокировкой, привязывает только записи без адресата; тот же userId — no-op, другой userId не назначается. Если после смены account email обнаружено самоприглашение, оно пропускается и остаётся `PENDING` до истечения. Ни роль, ни профиль, ни связь не создаются.

Отдельного `INV-06` background use case в v1 нет. `now >= expiresAt` проверяется при чтении и каждой значимой команде; только INV-01 материализует старое истёкшее приглашение перед новым. Это не влияет на ежедневную задачу VER-06, относящуюся к профильным токенам.

## 7. Relationship

`TutoringRelationshipQuery` реализует одиночную и пакетную проверку пары; `requireAllLinked` проверяет весь набор одним запросом и возвращает все отсутствующие ID через публичную ошибку. Создание `TeacherStudent` не является отдельным пользовательским use case: им владеет принятие приглашения.

`ListTeacherStudentsUseCase` и `ListStudentTeachersUseCase` берут владельца из доверенного контекста, требуют его профиль, загружают `limit + 1` связей с keyset cursor по `createdAt DESC, otherUserId DESC`, затем одним batch-вызовом получают linked-view профилей. Результаты содержат `linkedAt`, `birthDate` и только подтверждённый профильный email. `RelationshipCursorCodec` привязывает курсор к владельцу и направлению списка. Пустая страница содержит пустой список и `nextCursor = null`. `RelationshipResultMapper` не принимает полные self-view.

`TutoringRelationshipCommands.removeTeacherStudent` является внутренней доверенной командой внешнего `UnlinkStudentWorkflow`, а не самостоятельным REST endpoint. Workflow до удаления получает guard Scheduling для пары, обрабатывает будущие уроки и незавершённые запросы, затем вызывает Tutoring в той же транзакции. Tutoring блокирует пару и удаляет только `TeacherStudent`. Повтор с тем же `operationId` возвращает сохранённый результат; новая операция на отсутствующей паре получает `TeacherStudentNotLinkedException`. Любая ошибка откатывает Scheduling, outbox и удаление. Создание нового урока для этой пары должно пользоваться совместимым guard, иначе возможна гонка после проверки.

## 8. Identity event и общая email-блокировка

`IdentityAccountGateway` предоставляет `findAccountEmailState(userId)` и `findByVerifiedEmail(normalizedEmail)` через публичный Identity API. Он не возвращает `Identity.User`, пароль, роли или токены. Проверка роли при создании профиля выполняется разрешённым публичным Identity API. Техническая недоступность Identity не превращается в бизнес-ответ «не найдено».

Инфраструктурный listener переводит Identity event в `HandleAccountEmailVerifiedCommand(eventId, userId, verifiedEmail, verifiedAt)`. В write-транзакции `HandleAccountEmailVerifiedService` дедуплицирует `eventId`, берёт `NormalizedEmailLock`, **повторно подтверждает через Identity API**, что это текущий подтверждённый account email данного userId, затем:

1. подтверждает совпадающий current email каждого существующего профиля и аннулирует ставший ненужным запрос подтверждения этой цели;
2. если совпадает pending — продвигает его в current, подтверждает без письма Tutoring и аннулирует ставший ненужным запрос этой цели;
3. пакетно привязывает действующие приглашения на этот адрес по правилам INV-07;
4. сохраняет профили, приглашения и receipt события в той же транзакции.

Устаревшее событие о прежнем account email не подтверждает профиль и не привязывает приглашения; при успешно прочитанном актуальном состоянии Identity оно фиксируется как обработанный no-op. Недоступность Identity требует retry, а не такого receipt. Точный повтор `eventId` не делает изменений. Нет профилей/приглашений — нормальный успешный результат. Роль/профиль ученика и `TeacherStudent` событие не создаёт. Все операции создания/смены профильного email, INV-01 и этот обработчик используют один протокол email-блокировки, чтобы не пропустить подтверждение при гонке.

## 9. Идемпотентность и конкуренция

`CommandIdempotency.beginOrReplay(operationId, userId, operationType, payloadFingerprint, resultType)` вызывается в той же транзакции до бизнес-изменений. Новый ID возвращает `Proceed`; завершённый с тем же userId, типом, нормализованным payload и resultType — `Replay` с прежним результатом; иначе — публичный `IdempotencyConflictException`. `complete(...)` фиксирует результат и `completedAt` один раз. Нельзя завершить уже завершённую операцию. Уникальный `operationId` сериализует конкурентов; после rollback первой попытки другая может выполнить команду. Fingerprint, raw payload и токены не выводятся в ошибки и логи.

Отдельные IDEM-03/04/05 не создаются: replay, конфликт и конкурентный доступ входят в `beginOrReplay`. Для Identity event используется отдельный `ProcessedIdentityEventRepository`; отметка `eventId` коммитится вместе с эффектами. Алгоритм fingerprint, формат сохранённого результата и сроки хранения receipts — технические решения Persistence, не основание изменить поведение Application.

| Операция | Ключ | Повтор |
|---|---|---|
| Первоначальные профили и onboarding второй роли | `operationId` | Прежний typed result без повторного создания |
| Создание/принятие/отклонение приглашения | `operationId` | Прежний result без повторного письма/перехода |
| Удаление связи | `operationId` на уровне workflow и команды Tutoring | Без повторной обработки уроков или удаления |
| Подтверждение account email из Identity | `eventId` | No-op после успешного receipt |
| Read queries и обычные updates профилей | Нет | Чтение без записи; update не обещает replay |

## 10. Output ports: логический контракт

| Порт | Обязательные возможности |
|---|---|
| `SubjectRepository` | `findAll`, `exists`, пакетный `findExistingCodes` |
| `TeacherProfileRepository` / `StudentProfileRepository` | `findByUserId`, `findByUserIdForUpdate`, `existsByUserId`, пакетные summary/linked-проекции, явные `insert` и `update` без upsert |
| `ProfileEmailVerificationRepository` | поиск по `tokenHash`, повторное чтение `forUpdate`, активные запросы по `(userId, profileType, purpose)` с блокировкой, пакет истёкших для VER-06, явные `insert`/`update` |
| `StudentInvitationRepository` | `findByIdForUpdate`, действующие/старые `PENDING` по teacher+email, действующие `PENDING` по email для привязки, страницы отправленных/входящих с фильтром `asOf` и для входящих `attachedAt <= asOf` до limit, явные `insert`/`update`/`updateAll` |
| `TeacherStudentRepository` | `find`, `findForUpdate`, `exists`, пакетный `findLinkedStudentUserIds`, keyset `findByTeacher`/`findByStudent`, `insert`, `delete` |
| `CommandOperationRepository` / `ProcessedIdentityEventRepository` | атомарный reserve/replay и receipt с уникальными ключами |
| `IdentityAccountGateway` | состояние account email пользователя; точный поиск аккаунта по подтверждённому email |
| `NormalizedEmailLock` | транзакционная блокировка нормализованного адреса, одинаковая для профильной почты, INV-01 и Identity event |
| `ProfileVerificationTokenGenerator` / `Hasher` | криптографически стойкий raw token и детерминированный hash для поиска |
| `ProfileVerificationEmailQuota` | атомарно зарезервировать одно из пяти писем в rolling 24h по userId |
| `ProfileVerificationEmailSender` / `InvitationNotificationSender` | поставить durable заявку Notifications в той же транзакции, с dedup key и сроком отправки |
| `RelationshipCursorCodec` / `InvitationCursorCodec` | непрозрачный проверяемый cursor, привязанный к владельцу и виду списка |

Порты возвращают доменные типы или внутренние проекции, не jOOQ records и не чужие entity. Batch-методы не выполняют `N+1`. SQL, индексы, физический тип блокировки и сериализация cursor относятся к Infrastructure. Для приглашения понадобится расширить публичный Notifications API/шаблон: текущий verification-only gateway недостаточен; это отдельная интеграционная работа следующего этапа.

## 11. Транзакционная матрица

| Сценарий | Граница и блокировки | Атомарный результат |
|---|---|---|
| Subject, self/summary/linked profile, списки связей и приглашений | Read-only; без `operationId` | Без скрытых записей истечения |
| Обычное обновление профиля | Локальная write; профиль `forUpdate` | Обычные поля и предметы |
| PROF-11/12/13 | Внешняя Registration/RoleOnboarding write, вызов Tutoring в существующей транзакции | Identity user/role, профили, verification, заявка Notifications, idempotency |
| Смена/повторная проверка/подтверждение профильного email | Локальная write; email lock, профиль и verification, quota при письме | Профиль, запрос/токен, заявка, квота |
| VER-06 | Write по ограниченным batch | `expiredAt` verification, без изменения профиля |
| INV-01 | Локальная write; email lock, существующие приглашения | Старое истечение, новое приглашение, заявка письма, idempotency |
| INV-04/05 | Локальная write; приглашение `forUpdate` | Ответ и, для accept, пара `TeacherStudent`, idempotency |
| Identity email event | Локальная write; event receipt, email lock, затронутые профили и приглашения | Подтверждение/привязка и receipt |
| Удаление связи | Внешняя write `UnlinkStudentWorkflow`; Scheduling guard и пара `forUpdate` | Уроки/запросы, outbox Scheduling, удаление связи, idempotency |

Внешние доверенные Java-команды `TutoringRegistrationCommands` и `TutoringRelationshipCommands` требуют уже открытой транзакции (семантика `MANDATORY`). Команды с собственным пользовательским входом открывают локальную транзакцию на сервисной границе. Конкретные Spring-аннотации здесь не определяются.

## 12. Каталог ошибок и преобразование

Domain-исключения остаются внутренними. Application переводит их в ошибки сценария/публичного Java API; HTTP status выбирает Presentation позднее.

| Случай | Внешний контракт |
|---|---|
| Профиль отсутствует/уже есть/невалиден | `ProfileNotFoundException`, `ProfileAlreadyExistsException`, `InvalidProfileDataException` из API |
| Неизвестные предметы в публичной batch-проверке | `UnknownSubjectsException` со всеми кодами; внутренний SubjectNotFound не просачивается |
| Нет обязательной связи | `TeacherStudentNotLinkedException` из API |
| Тот же `operationId` с иными данными | `IdempotencyConflictException` из API |
| Приглашение отсутствует либо чужое | Одинаковая `InvitationUnavailableException`; ownership до состояния |
| Своё приглашение истекло или уже обработано | `InvitationResponseConflictException` с причиной `EXPIRED`/`ALREADY_RESPONDED` |
| Новое приглашение конфликтует с действующим, связью или самоприглашением | `InvitationCreationConflictException` с машинной причиной |
| Токен неизвестен/стар/использован/аннулирован/неактуален | Одна `InvalidProfileVerificationTokenException` |
| Превышены 5 новых писем за 24 часа | `ProfileEmailRateLimitException` с безопасным `retryAfter` |
| Cursor повреждён, чужой или для другого направления/фильтра | Ошибка cursor соответствующего блока, без раскрытия чужих данных |

Нарушение целостности (например, связь без профиля) — не нормальный `Optional.empty()` в списке. Недоступность Identity, Notifications enqueue или БД — техническая ошибка с rollback, не бизнес-«не найдено». Известный конфликт уникальности пары преобразуется в конфликт создания связи; иные нарушения ограничений не маскируются. Raw token, полный email/payload и fingerprint не включаются в тексты ошибок и логи.

## 13. Критерии завершения и синхронизация документов

- Профили обеих ролей независимы; дата рождения хранится в каждом и раскрывается другому пользователю только при `TeacherStudent`.
- `StudentProfile` может иметь пустые предметы, `TeacherProfile` — нет; специализация не ограничивает предмет урока.
- Self/summary/linked-view не смешиваются; batch-запросы обходятся без `N+1`.
- Account email не изменяется при смене профильного; pending и неподтверждённый current email не раскрываются другим пользователям.
- Токен истекает через 30 минут синхронно; VER-06 материализует истечение ежедневно; отдельного INV-06 нет.
- Событие Identity подтверждает совпадающий current **или pending** профильный email только после актуальной проверки account email через Identity API.
- INV-04 атомарно принимает приглашение и создаёт связь; внешнее удаление сначала согласует уроки с Scheduling.
- Raw token хранится только в Notifications delivery, Tutoring хранит hash. Письмо и бизнес-изменение ставятся в общую транзакцию; SMTP после commit не меняет результат.
- Этот этап не включает Java-код, SQL-миграции, OpenAPI или HTTP DTO.

Вместе с этим документом синхронизированы прежние спецификации: в relationship заменён self-view на linked-view; в integration обновлены обработка pending и актуальная проверка события; в Domain добавлены `expiredAt`/`expire(now)` verification; Subject batch error приведён к публичной `UnknownSubjectsException`; в этапе 2 уточнён пустой batch summary; invitation дополнен INV-07/08; в profile добавлен согласованный mapper. Физическая реализация БД, точные ограничения длины полей, cursor encoding, fingerprint schema, retention и client-side optimistic version остаются следующими техническими этапами.

Утверждение включает предметно-ориентированную структуру пакетов, имена новых портов и явный выбор `CURRENT`/`PENDING` для повторной проверки профильной почты. Оно не означает, что Java-код, SQL-миграции или тесты уже реализованы.
