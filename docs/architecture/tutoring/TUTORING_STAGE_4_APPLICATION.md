# Tutoring — этап 4. Итоговая спецификация Application

Статус: утверждён пользователем 2026-09-24. Документ объединяет согласованные сценарии и последующие уточнения обсуждения. Это архитектурная спецификация, а не Java-реализация.
Синхронизация от 2026-09-24: для приглашений учтён `attachedAt`, а логический `save` портов хранения уточнён до явных `insert`/`update`. Полный Persistence/Infrastructure/Presentation описан в `TUTORING_STAGE_5_INFRASTRUCTURE_AND_PRESENTATION.md`.

## 1. Граница Application

`tutoring.application` координирует агрегаты Tutoring, транзакции, порты хранения, публичный Java API Tutoring и интеграции через разрешённые API других модулей. Domain защищает локальные инварианты агрегатов; Application проверяет существование пользователя, роли, предметов и профилей, наличие связи, право раскрытия данных, идемпотентность и согласованность нескольких агрегатов.

Identity владеет аккаунтом, ролями, account email и единственной сохранённой `birthDate`. Tutoring владеет двумя независимыми профилями без даты рождения, их контактным email, предметами, приглашениями и связями. Scheduling владеет уроками; Notifications — очередью доставки писем приглашений. Application Tutoring не принимает HTTP DTO, не читает чужие таблицы, не обращается к `SecurityContext`, SMTP, jOOQ или Spring-событиям напрямую.

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
│   ├── port.in                  UpdateTeacherProfileUseCase, UpdateStudentProfileUseCase,
│   │                            ChangeProfileEmailUseCase, GetPublicProfileUseCase,
│   │                            SearchPublicProfilesUseCase
│   ├── port.out                 TeacherProfileRepository, StudentProfileRepository
│   ├── model.command            UpdateTeacherProfileCommand, UpdateStudentProfileCommand,
│   │                            ChangeProfileEmailCommand
│   ├── model.query              LinkedProfileBatchQuery, PublicProfileSearchQuery
│   ├── service                  ProfileQueryService, PublicProfileSearchService,
│   │                            LinkedProfileProjectionService,
│   │                            TeacherProfileUpdateService, StudentProfileUpdateService,
│   │                            ChangeProfileEmailService, RegistrationProfileService
│   └── mapper                   ProfileViewMapper
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

`TeacherStudentNotLinkedException`, `ProfileNotFoundException`, `ProfileAlreadyExistsException`, `InvalidProfileDataException`, `UnknownSubjectsException` и `IdempotencyConflictException` остаются в `tutoring.api.exception`: Application не создаёт их дубликаты. Типы self/public/summary-view и регистрационные команды также остаются в `tutoring.api`. Имена новых внутренних классов в дереве фиксируют намерение; сигнатуры портов ниже являются логическими контрактами без привязки к Spring или SQL.

Общая `NormalizedEmailLock` нужна для создания приглашений и обработки Identity-события. Её метод `lock(normalizedEmail)` требует активной write-транзакции; блокировка удерживается до её завершения. Реализация определяется Infrastructure.

### 2.1 Логические сигнатуры входных сценариев

Обозначение `execute(command)` ниже задаёт контракт Application, не HTTP endpoint. `actorUserId` всегда доверенный; nullable-поля и проверки входа описаны в сценариях соответствующих блоков.

| Интерфейс | Метод и результат |
|---|---|
| `GetSubjectsUseCase` | `List<SubjectResult> getSubjects()` |
| `GetPublicProfileUseCase` | `Optional<PublicTeacherProfileView/PublicStudentProfileView> get(type,userId)`; только ACTIVE аккаунт |
| `SearchPublicProfilesUseCase` | `PublicProfilePageResult search(type,subjectCode,minAge,maxAge,cursor,limit)` |
| `UpdateTeacherProfileUseCase` | `void execute(UpdateTeacherProfileCommand)` |
| `UpdateStudentProfileUseCase` | `void execute(UpdateStudentProfileCommand)` |
| `ChangeProfileEmailUseCase` | `void execute(ChangeProfileEmailCommand)`; адрес заменяется сразу |
| `CreateStudentInvitationUseCase` | `InvitationCreatedResult execute(CreateStudentInvitationCommand)` |
| `ListSentInvitationsUseCase` | `SentInvitationPageResult execute(SentInvitationsQuery)` |
| `ListIncomingInvitationsUseCase` | `IncomingInvitationPageResult execute(IncomingInvitationsQuery)` |
| `AcceptStudentInvitationUseCase` | `InvitationAcceptedResult execute(AcceptStudentInvitationCommand)` |
| `RejectStudentInvitationUseCase` | `InvitationRejectedResult execute(RejectStudentInvitationCommand)` |
| `ListTeacherStudentsUseCase` | `StudentRelationshipPageResult execute(ListTeacherStudentsQuery)` |
| `ListStudentTeachersUseCase` | `TeacherRelationshipPageResult execute(ListStudentTeachersQuery)` |
| `HandleAccountEmailVerifiedUseCase` | `void handle(HandleAccountEmailVerifiedCommand)` |

`ChangeProfileEmailCommand` содержит `(actorUserId, profileType, newEmail)` и не запускает подтверждение адреса. Создание/ответ на приглашение содержит `operationId`, доверенный actor и email либо `invitationId`. Query списка содержит доверенный actor, фильтр статуса, cursor и limit. Точные имена полей result-моделей должны совпадать с описанным в разделах 4–7 составом, без скрытых account/role данных.

## 3. Subject

`GetSubjectsUseCase.getSubjects(): List<SubjectResult>` не принимает параметров и не проверяет пользователя. Сервис вызывает `SubjectRepository.findAll()`, преобразует `Subject` в `SubjectResult(subjectCode, name)`, сортирует по `name ASC, subjectCode ASC` и возвращает неизменяемый список. Пустой справочник — утверждённая внутренняя ошибка `SubjectNotFoundException`.

Публичный `TutoringSubjectQuery.exists(String)` возвращает `false` для корректного, но отсутствующего кода. `requireAllExist(Set<String>)` одним batch-запросом определяет все отсутствующие коды и выдаёт публичную `UnknownSubjectsException`. `InvalidSubjectCodeException`/`SubjectNotFoundException` в документе Subject являются внутренними ошибками; прямой выброс `SubjectNotFoundException` из публичного `requireAllExist` исключён. Специализация преподавателя не ограничивает предмет урока.

## 4. Profile

### 4.1 Чтение и раскрытие данных

`ProfileQueryService` реализует `TutoringProfileQuery`. `findTeacherProfile(userId)` и `findStudentProfile(userId)` возвращают полный self-view или `Optional.empty()`, но доступны для показа только через `MeQueryFacade`, сверяющий `principal.userId == userId`. Self-view содержит вычисленный возраст и контактный email, но не дату рождения, роль, пароль или account email.

`findTeacherSummaries(Set<UUID>)` и `findStudentSummaries(Set<UUID>)` делают один batch-запрос и возвращают `Map<UUID, Summary>` только для найденных профилей. Пустой набор даёт пустую карту без БД; это уточнение позднего сценария PROF-03/04 имеет приоритет над общим правилом этапа 2 о пустом batch. Summary содержит только `userId` и `displayName`.

Публичное чтение отдельного профиля и поиск не требуют principal или `TeacherStudent`. `GetPublicProfileUseCase` отдаёт ACTIVE профиль или `Optional.empty()`; `SearchPublicProfilesUseCase` фильтрует выбранные предметы по таблице соответствующего профиля, а возраст и активность получает из Identity пакетами по ID кандидатов. Поиск сортирует по `createdAt DESC,userId DESC`, добирает до `limit + 1` подходящих записей и подписывает курсор с фильтрами и датой расчёта возраста UTC. Контактный email профиля показывается как введённый пользователем адрес без статуса подтверждения. Ошибка Identity не превращается в пустую страницу.

`LinkedProfileProjectionService` пакетно собирает `PublicStudentProfileView`/`PublicTeacherProfileView` только для ID, которые `relationship` уже получил из подтверждённых связей. Эти модели содержат возраст из Identity, обычные сведения, `contactDetails` и указанный `contactEmail`. Публичное чтение профиля выполняется отдельным use case без связи и проверяет активность пользователя Identity. Отсутствующий профиль при существующей связи — ошибка целостности, не неполная карточка. Для одиночного `findLinked*Profile` сервис relationship сначала проверяет связь; без связи возвращает `Optional.empty()` только для linked-операции.

`ProfileViewMapper` имеет операции для self, public и summary; для списка связей используется та же public-модель после проверки `TeacherStudent`. `RelationshipResultMapper` принимает public-view, не self-view. Mapper не обращается к репозиториям, Identity или Notifications и не принимает решение о наличии права на просмотр.

### 4.2 Обычные изменения

`UpdateTeacherProfileUseCase` полностью заменяет обычные поля `displayName`, `contactDetails`, `subjectCodes`, `description`, `education`, `experienceYears`, `city`, `photoUrl`; `UpdateStudentProfileUseCase` — `displayName`, `contactDetails`, `subjectCodes`, `photoUrl`. Это не PATCH. Сервис проверяет коды предметов пакетно, загружает собственный профиль с блокировкой, вызывает `updateDetails` и `changeSubjects`, сохраняет агрегат. У преподавателя предметов минимум один; у ученика допустим пустой набор, при котором справочник не запрашивается. Результат `void`, `operationId` не нужен.

Контактный email и `userId` обычные update-команды не меняют; для адреса есть отдельный `ChangeProfileEmailUseCase`. Блокировка сериализует записи, но защита от stale browser form через `expectedVersion` отдельно не обещана; это решение Persistence/Presentation.

### 4.3 Создание профилей

`RegistrationProfileService` реализует публичные `createInitialProfiles`, `createTeacherProfile` и `createStudentProfile`. Каждая команда валидирует профильные данные, пакетно проверяет предметы, требует отсутствие целевого профиля и наличие уже созданной роли через публичный Identity API, затем создаёт профиль. Ключ повтора и итоговый результат сохраняет Workflows в одной транзакции с профилем.

`createInitialProfiles` требует хотя бы один из двух `Optional`; при двух ролях создаёт оба профиля атомарно. Внешний `RegistrationWorkflow` создаёт Identity.User и согласует выбранные роли с данными профилей; Tutoring проверяет лишь существование соответствующих ролей. Внешний `RoleOnboardingWorkflow` сначала добавляет вторую роль через Identity и вызывает одну типизированную команду Tutoring без даты рождения. Ошибка Tutoring откатывает и создание пользователя/роли во внешнем workflow. У двух профилей одного пользователя почта и предметы независимы.

Первоначальный `contactEmail` сохраняется как указанный пользователем контактный адрес без подтверждения. Публичный результат содержит идентификаторы созданных профилей, но не HTTP DTO.

## 5. Контактный email профиля

`ChangeProfileEmailUseCase` получает доверенный `actorUserId`, тип профиля и новый контактный адрес. Сервис загружает собственный профиль, проверяет формат через `ProfileEmail`, вызывает `changeContactEmail` и сразу сохраняет результат. Повтор текущего адреса не меняет состояние. Подтверждение владения, pending-адрес, токены, квота и отправка письма для профильного адреса отсутствуют.

Контактный адрес может отличаться от account email Identity. Он публичен как введённые пользователем данные и не используется для входа, подтверждения личности, системных уведомлений или поиска получателя приглашения.

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

Отдельного `INV-06` background use case в v1 нет. `now >= expiresAt` проверяется при чтении и каждой значимой команде; только INV-01 материализует старое истёкшее приглашение перед новым. Задачи очистки профильных токенов нет, поскольку контактный email профиля не подтверждается.

## 7. Relationship

`TutoringRelationshipQuery` реализует одиночную и пакетную проверку пары; `requireAllLinked` проверяет весь набор одним запросом и возвращает все отсутствующие ID через публичную ошибку. Создание `TeacherStudent` не является отдельным пользовательским use case: им владеет принятие приглашения.

`ListTeacherStudentsUseCase` и `ListStudentTeachersUseCase` берут владельца из доверенного контекста, требуют его профиль, загружают `limit + 1` связей с keyset cursor по `createdAt DESC, otherUserId DESC`, затем одним batch-вызовом получают public-view профилей участников связей. Результаты содержат `linkedAt`, вычисленный возраст и указанный контактный email. `RelationshipCursorCodec` привязывает курсор к владельцу и направлению списка. Пустая страница содержит пустой список и `nextCursor = null`. `RelationshipResultMapper` не принимает полные self-view.

`TutoringRelationshipCommands.removeTeacherStudent` является внутренней доверенной командой внешнего `UnlinkStudentWorkflow`, а не самостоятельным REST endpoint. Workflow до удаления получает guard Scheduling для пары, обрабатывает будущие уроки и незавершённые запросы, затем вызывает Tutoring в той же транзакции. Tutoring блокирует пару и удаляет только `TeacherStudent`. Повтор с тем же `operationId` возвращает сохранённый результат; новая операция на отсутствующей паре получает `TeacherStudentNotLinkedException`. Любая ошибка откатывает Scheduling, outbox и удаление. Создание нового урока для этой пары должно пользоваться совместимым guard, иначе возможна гонка после проверки.

## 8. Identity event и общая email-блокировка

`IdentityAccountGateway` предоставляет `findByVerifiedEmail(normalizedEmail)`, проверку роли и пакетное получение возраста через публичный Identity API. Он не возвращает `Identity.User`, пароль, дату рождения или токены. Техническая недоступность Identity не превращается в бизнес-ответ «не найдено».

Инфраструктурный listener переводит Identity event в `HandleAccountEmailVerifiedCommand(eventId, userId, verifiedEmail, verifiedAt)`. В write-транзакции `HandleAccountEmailVerifiedService` дедуплицирует `eventId`, берёт `NormalizedEmailLock`, **повторно подтверждает через Identity API**, что это текущий подтверждённый account email данного userId, затем пакетно привязывает действующие приглашения на адрес и сохраняет приглашения с receipt события в той же транзакции.

Устаревшее событие о прежнем account email не привязывает приглашения; при успешно прочитанном актуальном состоянии Identity оно фиксируется как обработанный no-op. Недоступность Identity требует retry, а не такого receipt. Точный повтор `eventId` не делает изменений. Отсутствие приглашений — нормальный успешный результат. Роль/профиль ученика и `TeacherStudent` событие не создаёт. Создание приглашения и этот обработчик используют один протокол email-блокировки, чтобы не пропустить привязку при гонке.

## 9. Идемпотентность и конкуренция

Для самостоятельных команд Tutoring `CommandIdempotency.beginOrReplay(operationId, userId, operationType, payloadFingerprint, resultType)` вызывается в той же транзакции до бизнес-изменений. Новый ID возвращает `Proceed`; завершённый с тем же userId, типом, нормализованным payload и resultType — `Replay` с прежним результатом; иначе — публичный `IdempotencyConflictException`. `complete(...)` фиксирует результат и `completedAt` один раз. Нельзя завершить уже завершённую операцию. Уникальный `operationId` сериализует конкурентов; после rollback первой попытки другая может выполнить команду. Fingerprint, raw payload и токены не выводятся в ошибки и логи.

Отдельные IDEM-03/04/05 не создаются: replay, конфликт и конкурентный доступ входят в `beginOrReplay`. Для Identity event используется отдельный `ProcessedIdentityEventRepository`; отметка `eventId` коммитится вместе с эффектами. Алгоритм fingerprint, формат сохранённого результата и сроки хранения receipts — технические решения Persistence, не основание изменить поведение Application.

| Операция | Ключ | Повтор |
|---|---|---|
| Первоначальные профили и onboarding второй роли | `Idempotency-Key` в Workflows | Прежний итоговый ответ без повторного вызова Tutoring |
| Создание/принятие/отклонение приглашения | `operationId` | Прежний result без повторного письма/перехода |
| Удаление связи | `operationId` на уровне workflow и команды Tutoring | Без повторной обработки уроков или удаления |
| Подтверждение account email из Identity | `eventId` | No-op после успешного receipt |
| Read queries и обычные updates профилей | Нет | Чтение без записи; update не обещает replay |

## 10. Output ports: логический контракт

| Порт | Обязательные возможности |
|---|---|
| `SubjectRepository` | `findAll`, `exists`, пакетный `findExistingCodes` |
| `TeacherProfileRepository` / `StudentProfileRepository` | `findByUserId`, `findByUserIdForUpdate`, `existsByUserId`, пакетные summary/linked-проекции, явные `insert` и `update` без upsert |
| `StudentInvitationRepository` | `findByIdForUpdate`, действующие/старые `PENDING` по teacher+email, действующие `PENDING` по email для привязки, страницы отправленных/входящих с фильтром `asOf` и для входящих `attachedAt <= asOf` до limit, явные `insert`/`update`/`updateAll` |
| `TeacherStudentRepository` | `find`, `findForUpdate`, `exists`, пакетный `findLinkedStudentUserIds`, keyset `findByTeacher`/`findByStudent`, `insert`, `delete` |
| `CommandOperationRepository` / `ProcessedIdentityEventRepository` | атомарный reserve/replay и receipt с уникальными ключами |
| `IdentityAccountGateway` | точный поиск аккаунта по подтверждённому email, проверка роли и пакетное получение возраста |
| `NormalizedEmailLock` | транзакционная блокировка нормализованного account email для приглашения и Identity event |
| `InvitationNotificationSender` | поставить durable заявку письма приглашения в той же транзакции, с dedup key и сроком отправки |
| `RelationshipCursorCodec` / `InvitationCursorCodec` | непрозрачный проверяемый cursor, привязанный к владельцу и виду списка |

Порты возвращают доменные типы или внутренние проекции, не jOOQ records и не чужие entity. Batch-методы не выполняют `N+1`. SQL, индексы, физический тип блокировки и сериализация cursor относятся к Infrastructure. Для приглашения понадобится расширить публичный Notifications API/шаблон: текущий verification-only gateway недостаточен; это отдельная интеграционная работа следующего этапа.

## 11. Транзакционная матрица

| Сценарий | Граница и блокировки | Атомарный результат |
|---|---|---|
| Subject, self/summary/linked profile, списки связей и приглашений | Read-only; без `operationId` | Без скрытых записей истечения |
| Обычное обновление профиля | Локальная write; профиль `forUpdate` | Обычные поля и предметы |
| PROF-11/12/13 | Внешняя Registration/RoleOnboarding write, вызов Tutoring в существующей транзакции | Identity user/role, профили, журнал Workflows |
| Смена контактного email | Локальная write; профиль `forUpdate` | Сразу сохранённый адрес без письма или подтверждения |
| INV-01 | Локальная write; email lock, существующие приглашения | Старое истечение, новое приглашение, заявка письма, idempotency |
| INV-04/05 | Локальная write; приглашение `forUpdate` | Ответ и, для accept, пара `TeacherStudent`, idempotency |
| Identity email event | Локальная write; event receipt, email lock и приглашения | Привязка приглашений и receipt |
| Удаление связи | Внешняя write `UnlinkStudentWorkflow`; Scheduling guard и пара `forUpdate` | Уроки/запросы, outbox Scheduling, удаление связи, idempotency |

Внешние доверенные Java-команды `TutoringProfileCreationCommands` и `TutoringRelationshipCommands` требуют уже открытой транзакции (семантика `MANDATORY`). Команды с собственным пользовательским входом открывают локальную транзакцию на сервисной границе. Конкретные Spring-аннотации здесь не определяются.

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
| Cursor повреждён, чужой или для другого направления/фильтра | Ошибка cursor соответствующего блока, без раскрытия чужих данных |

Нарушение целостности (например, связь без профиля) — не нормальный `Optional.empty()` в списке. Недоступность Identity, Notifications enqueue или БД — техническая ошибка с rollback, не бизнес-«не найдено». Известный конфликт уникальности пары преобразуется в конфликт создания связи; иные нарушения ограничений не маскируются. Полный email/payload и fingerprint не включаются в тексты ошибок и логи.

## 13. Критерии завершения и синхронизация документов

- Профили обеих ролей независимы и не хранят дату рождения или возраст; Identity вычисляет возраст для self/public-view, а точную дату показывает владельцу в данных аккаунта.
- `StudentProfile` может иметь пустые предметы, `TeacherProfile` — нет; специализация не ограничивает предмет урока.
- Self/summary/public-view не смешиваются; для списка связей public-view строится после проверки отношения, batch-запросы обходятся без `N+1`.
- Account email не изменяется при смене контактного адреса; новый профильный email публикуется сразу как данные, введённые пользователем.
- Событие Identity привязывает приглашения к подтверждённому account email, не изменяя профильные адреса.
- INV-04 атомарно принимает приглашение и создаёт связь; внешнее удаление сначала согласует уроки с Scheduling.
- Письмо приглашения и бизнес-изменение ставятся в общую транзакцию; SMTP после commit не меняет результат.
- Этот этап не включает Java-код, SQL-миграции, OpenAPI или HTTP DTO.

Вместе с этим документом синхронизированы прежние спецификации: в relationship полное self-view заменено на публичную модель после проверки связи; событие Identity оставлено для приглашений; Subject batch error приведён к публичной `UnknownSubjectsException`; invitation дополнен INV-07/08; в profile добавлен согласованный mapper. Физическая реализация БД, точные ограничения длины полей, cursor encoding, fingerprint schema, retention и client-side optimistic version остаются следующими техническими этапами.

Утверждение включает предметно-ориентированную структуру пакетов и имена новых портов. Контактная почта профиля не имеет состояния подтверждения. Документ сам по себе не означает, что Java-код, SQL-миграции или тесты реализованы.
