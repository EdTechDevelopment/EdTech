# Tutoring — этап 4. Итоговая спецификация Application

Статус: утверждён пользователем 2026-09-24. Документ объединяет согласованные сценарии и последующие уточнения обсуждения. Это архитектурная спецификация, а не Java-реализация.
Синхронизация от 2026-09-24: для приглашений учтён `attachedAt`, а логический `save` портов хранения уточнён до явных `insert`/`update`. Полный Persistence/Infrastructure/Presentation описан в `TUTORING_STAGE_5_INFRASTRUCTURE_AND_PRESENTATION.md`.

## 1. Граница Application

`tutoring.application` координирует агрегаты Tutoring, транзакции, порты хранения, публичный Java API Tutoring и интеграции через разрешённые API других модулей. Domain защищает локальные инварианты агрегатов; Application проверяет существование пользователя, роли, предметов и профилей, наличие связи, право раскрытия данных, идемпотентность и согласованность нескольких агрегатов.

Identity владеет аккаунтом, ролями, account email и единственной сохранённой `birthDate`. Tutoring владеет двумя независимыми профилями без даты рождения, их контактным email, предметами, приглашениями и связями. Scheduling владеет уроками; Notifications — очередью доставки писем приглашений. Application Tutoring не принимает HTTP DTO, не читает чужие таблицы, не обращается к `SecurityContext`, SMTP, jOOQ или Spring-событиям напрямую.

Время получает сервис через `Clock` и явно передаёт в Domain. Публичные межмодульные интерфейсы остаются в `tutoring.api`; для доверенных межмодульных команд дополнительные внутренние `port.in` не создаются. `GetPublicProfileUseCase` описывает вход Presentation и реализуется теми же методами `ProfileQueryService`, что публичные методы `TutoringProfileQuery`, без копирования логики. Пользовательские use case получают `actorUserId` только от доверенной presentation/workflow-границы. Ни один сервис Tutoring не добавляет роль Identity.

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
│   └── exception                SubjectNotFoundException
├── profile
│   ├── port.in                  UpdateTeacherProfileUseCase, UpdateStudentProfileUseCase,
│   │                            GetPublicProfileUseCase, SearchPublicProfilesUseCase
│   ├── port.out                 TeacherProfileRepository, StudentProfileRepository,
│   │                            PublicProfileCursorCodec
│   ├── model.command            UpdateTeacherProfileCommand, UpdateStudentProfileCommand
│   ├── model.query              PublicProfileSearchQuery, PublicProfilePageCursor,
│   │                            ProfileNameData, PublicProfileSearchCandidate
│   ├── model.result             PublicTeacherProfilePageResult, PublicStudentProfilePageResult
│   ├── service                  ProfileQueryService, PublicProfileSearchService,
│   │                            LinkedProfileProjectionService,
│   │                            TeacherProfileUpdateService, StudentProfileUpdateService,
│   │                            RegistrationProfileService
│   ├── mapper                   ProfileInputMapper, ProfileViewMapper
│   └── exception                InvalidPublicProfileSearchException,
│                                InvalidPublicProfileCursorException, ProfileReadConsistencyException
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
│   │                            InvitationQueryService,
│   │                            StudentInvitationResponseService
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
│   ├── service                  RelationshipListQueryService,
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
│   └── exception                IdentityUserConsistencyException
├── idempotency
│   ├── port.in                  CommandIdempotency
│   ├── port.out                 CommandOperationRepository,
│   │                            ProcessedIdentityEventRepository
│   ├── model                    OperationType, PayloadFingerprint,
│   │                            CommandOperation,
│   │                            ProcessedIdentityEvent
│   ├── service                  CommandIdempotencyService,
│   │                            IdentityEventDeduplicationService
│   └── exception                InvalidIdentityEventException
└── email
    └── port.out                 NormalizedEmailLock
```

`TeacherStudentNotLinkedException`, `ProfileNotFoundException`, `ProfileAlreadyExistsException`, `InvalidProfileDataException`, `UnknownSubjectsException` и `IdempotencyConflictException` остаются в `tutoring.api.exception`: Application не создаёт их дубликаты. Общие `TeacherProfileView`/`StudentProfileView`, отдельные summary-модели и регистрационные команды также остаются в `tutoring.api`. Имена новых внутренних классов в дереве фиксируют намерение; сигнатуры портов ниже являются логическими контрактами без привязки к Spring или SQL.

`InvitationQueryService` реализует оба входных интерфейса списков приглашений; `StudentInvitationResponseService` — интерфейсы принятия и отклонения. `RelationshipListQueryService` реализует оба направления списка связей. Входные интерфейсы остаются раздельными, потому что это разные действия с разными запросами и ответами; общая реализация убирает повторение почти одинаковых сервисных классов. Привязка приглашений после подтверждения почты выполняется приватным методом `HandleAccountEmailVerifiedService`, так как у этого шага только один вызывающий сценарий.

Общая `NormalizedEmailLock` нужна для создания приглашений и обработки Identity-события. Её метод `lock(normalizedEmail)` требует активной write-транзакции; блокировка удерживается до её завершения. Реализация определяется Infrastructure.

### 2.1 Логические сигнатуры входных сценариев

Обозначение `execute(command)` ниже задаёт контракт Application, не HTTP endpoint. `actorUserId` всегда доверенный; nullable-поля и проверки входа описаны в сценариях соответствующих блоков.

| Интерфейс | Метод и результат |
|---|---|
| `GetSubjectsUseCase` | `List<SubjectResult> getSubjects()` |
| `GetPublicProfileUseCase` | `findPublicTeacherProfile(userId)` → `Optional<TeacherProfileView>`; `findPublicStudentProfile(userId)` → `Optional<StudentProfileView>`; только ACTIVE аккаунт |
| `SearchPublicProfilesUseCase` | `searchTeachers(PublicProfileSearchQuery)` → `PublicTeacherProfilePageResult`; `searchStudents(PublicProfileSearchQuery)` → `PublicStudentProfilePageResult` |
| `UpdateTeacherProfileUseCase` | `void execute(UpdateTeacherProfileCommand)` |
| `UpdateStudentProfileUseCase` | `void execute(UpdateStudentProfileCommand)` |
| `CreateStudentInvitationUseCase` | `InvitationCreatedResult execute(CreateStudentInvitationCommand)` |
| `ListSentInvitationsUseCase` | `SentInvitationPageResult execute(SentInvitationsQuery)` |
| `ListIncomingInvitationsUseCase` | `IncomingInvitationPageResult execute(IncomingInvitationsQuery)` |
| `AcceptStudentInvitationUseCase` | `InvitationAcceptedResult execute(AcceptStudentInvitationCommand)` |
| `RejectStudentInvitationUseCase` | `InvitationRejectedResult execute(RejectStudentInvitationCommand)` |
| `ListTeacherStudentsUseCase` | `StudentRelationshipPageResult execute(ListTeacherStudentsQuery)` |
| `ListStudentTeachersUseCase` | `TeacherRelationshipPageResult execute(ListStudentTeachersQuery)` |
| `HandleAccountEmailVerifiedUseCase` | `void handle(HandleAccountEmailVerifiedCommand)` |

Команды обновления профилей содержат доверенный `actorUserId`, `displayName`, обязательный `contactEmail`, `contactDetails`, `subjectCodes` и обычные поля соответствующего профиля. Создание/ответ на приглашение содержит `operationId`, доверенный actor и email либо `invitationId`. Query списка содержит доверенный actor, фильтр статуса, cursor и limit. Точные имена полей result-моделей должны совпадать с описанным в разделах 4–7 составом, без скрытых account/role данных.

## 3. Subject

`GetSubjectsUseCase.getSubjects(): List<SubjectResult>` не принимает параметров и не проверяет пользователя. Сервис вызывает `SubjectRepository.findAll()`, преобразует `Subject` в `SubjectResult(subjectCode, name)`, сортирует по `name ASC` и возвращает неизменяемый список. Названия предметов уникальны, поэтому сортировка по коду при совпадении названий не нужна. Пустой справочник — утверждённая внутренняя ошибка `SubjectNotFoundException`.

Публичный `TutoringSubjectQuery.exists(String)` возвращает `false` для корректного, но отсутствующего кода. `requireAllExist(Set<String>)` одним batch-запросом определяет все отсутствующие коды и выдаёт публичную `UnknownSubjectsException`. Синтаксис проверяет уже существующий доменный `SubjectCode`; второго `InvalidSubjectCodeException` в Application нет. Некорректный аргумент публичного Java API преобразуется в `IllegalArgumentException`. `SubjectNotFoundException` остаётся внутренней ошибкой пустого каталога и не выходит из `requireAllExist`. Специализация преподавателя не ограничивает предмет урока.

## 4. Profile

### 4.1 Чтение и раскрытие данных

`ProfileQueryService` реализует `TutoringProfileQuery`. `findTeacherProfile(userId)` и `findStudentProfile(userId)` возвращают данные собственного профиля или `Optional.empty()`; показ владельцу проходит через `MeQueryFacade`, сверяющий `principal.userId == userId`. Те же `TeacherProfileView` и `StudentProfileView` используются для публичного и linked-чтения: доступ определяет сценарий и проверки, а не отдельная модель с теми же полями. Представление содержит вычисленный возраст и контактный email, но не дату рождения, роль, пароль или account email. Возраст не хранится в профиле; при чтении Tutoring получает его от Identity.

`findTeacherSummaries(Set<UUID>)` и `findStudentSummaries(Set<UUID>)` делают один batch-запрос и возвращают `Map<UUID, Summary>` только для найденных профилей. Пустой набор даёт пустую карту без БД; это уточнение позднего сценария PROF-03/04 имеет приоритет над общим правилом этапа 2 о пустом batch. Summary содержит только `userId` и `displayName`.

Публичное чтение отдельного профиля и поиск не требуют principal или `TeacherStudent`. `GetPublicProfileUseCase` отдаёт ACTIVE профиль или `Optional.empty()`. У поиска два входа: преподаватели и ученики. Общий алгоритм перебирает кандидатов, получает активность и возраст пакетно через Identity и выбирает подходящие результаты. Размер страницы фиксирован — 20 карточек; пользователь его не передаёт. Для проверки следующей страницы алгоритм читает до 21-го подходящего кандидата. Кандидаты идут по `createdAt DESC,userId DESC`; `createdAt` — неизменяемое поле доменной модели и ключ порядка в каталоге. После отбора репозиторий пакетно загружает профили только для 20 карточек, которые уйдут в ответ. Для открытия подробностей используется отдельный публичный запрос полного профиля по `userId`. `PublicProfileCursorCodec` — выходной порт для создания и проверки курсора с фильтрами, позицией и датой расчёта возраста UTC; техническую подпись реализует Infrastructure. Ошибка Identity не превращается в пустую страницу.

`LinkedProfileProjectionService.findTeachers(Set<UUID>)` / `findStudents(Set<UUID>)` пакетно собирают `TeacherProfileView`/`StudentProfileView` только для ID, которые relationship уже получил из подтверждённых связей. Каждый метод делает один batch профилей, проверяет наличие всех запрошенных профилей, получает активные ID и возраст активных аккаунтов. Неактивные аккаунты в ответ не входят; сами связи не удаляются. Пустой набор сразу возвращает пустую карту. Дополнительный `LinkedProfileBatchQuery` не создаётся: для этих внутренних операций достаточно набора проверенных ID. Отсутствующий профиль при существующей связи — `ProfileReadConsistencyException`. Одиночные `findLinked*Profile` в `ProfileQueryService` сначала вызывают `TeacherStudentRepository.exists`, затем читают целевой профиль и проверяют ACTIVE; нет связи или аккаунт неактивен — `Optional.empty()`.

Подэтап 4.3 расширяет `IdentityAccountGateway` методами `findAgesByIds(userIds, asOf)` и `findActiveUserIds(userIds)`. Возраст возвращается для существующих аккаунтов независимо от статуса; публичный и linked-ответ дополнительно требуют ACTIVE. Нет профиля при обычном lookup — `Optional.empty()`; нет возраста у найденного профиля или возраст отрицательный — `ProfileReadConsistencyException`. Технический сбой Identity не маскируется под отсутствие результата.

`PublicProfileSearchQuery(subjectCode, minAge, maxAge, cursor)` проверяет синтаксис кода, неотрицательный возраст и `maxAge >= minAge`. Размер страницы задаёт Application и он всегда равен 20; параметра `limit` во входном запросе нет. Пустой cursor не равен первой странице и отклоняется. Наличие кода в справочнике проверяется через `TutoringSubjectQuery` до чтения профилей; неизвестный код даёт `UnknownSubjectsException`. Ошибки фильтров — `InvalidPublicProfileSearchException`; курсора — `InvalidPublicProfileCursorException`.

`PublicProfileSearchCandidate` содержит только `userId` и `createdAt`. Репозиторий отдаёт до 21 кандидата за запрос в порядке DESC/DESC. Application проверяет активность и возраст всей порции и продолжает чтение таких же порций, пока не найдёт 21 подходящий профиль или не достигнет конца каталога. В ответ входят первые 20; 21-й показывает, что есть следующая страница. Курсор указывает на последний показанный профиль, поэтому 21-й не теряется и будет просмотрен снова следующим запросом. При фильтрации могут также повторно просматриваться кандидаты после границы страницы, которые не попали в ответ. Это намеренное допущение MVP: один lookahead-результат нужен, чтобы точно сообщить о следующей странице, а курсор на последней показанной карточке не пропускает её первый результат. Потенциальное повторное чтение этой небольшой области принято ради простого продолжения списка; отдельное хранилище lookahead-результата не вводится. Если фильтры отсеивают кандидатов, сервис читает дополнительные порции, чтобы заполнить страницу или убедиться, что каталог закончился. После выбора страницы профили пакетно читаются по их ID и отображаются как короткие карточки; подробные поля в ответ поиска не входят. UTC-дата возраста сохраняется с первой страницы; будущая дата в курсоре отклоняется. Курсор привязан к виду профиля и фильтрам subjectCode/minAge/maxAge. Подпись и проверку структуры реализует Infrastructure.

`ProfileViewMapper` собирает `TeacherProfileView` и `StudentProfileView` для собственного, подробного публичного и linked-чтения; для поиска формирует отдельные короткие карточки. В карточке преподавателя описание обрезается до 200 символов, а в карточке ученика остаются имя, возраст, фото и предметы. Для summary mapper преобразует `ProfileNameData` в отдельные минимальные публичные модели имён. Mapper не обращается к репозиториям, Identity или Notifications и не принимает решение о наличии права на просмотр.

### 4.2 Обычные изменения

`UpdateTeacherProfileUseCase` полностью заменяет поля `displayName`, `contactEmail`, `contactDetails`, `subjectCodes`, `description`, `education`, `experienceYears`, `city`, `photoUrl`; `UpdateStudentProfileUseCase` — `displayName`, `contactEmail`, `contactDetails`, `subjectCodes`, `photoUrl`. Это не PATCH. Сервис проверяет коды предметов пакетно, загружает собственный профиль с блокировкой, затем преобразует команду через `ProfileInputMapper`, передавая прежний `createdAt`, вызывает `updateDetails`, `changeSubjects` и `changeContactEmail`, сохраняет агрегат. У преподавателя предметов минимум один; у ученика допустим пустой набор, при котором справочник не запрашивается. Обновление не меняет `createdAt`. Результат `void`, `operationId` не нужен.

`userId` профиля не меняется. Контактный email входит в обычное обновление и проверяется через `ProfileEmail`. Блокировка сериализует записи, но защита от stale browser form через `expectedVersion` отдельно не обещана; это решение Persistence/Presentation.

### 4.3 Создание профилей

`RegistrationProfileService` реализует публичные `createInitialProfiles`, `createTeacherProfile` и `createStudentProfile`. Каждая команда валидирует профильные данные, пакетно проверяет предметы, требует отсутствие целевого профиля и наличие уже созданной роли через публичный Identity API, затем создаёт профиль. Application получает время через `Clock`; для двух профилей одной начальной регистрации время фиксируется один раз. Ключ повтора и итоговый результат сохраняет Workflows в одной транзакции с профилем.

В подэтапе 4.2 сервис использует `IdentityAccountGateway.requireRole(userId, ProfileType)`; отсутствие аккаунта или нужной роли — `IdentityUserConsistencyException`. Статус ACTIVE и подтверждение почты для первоначальных профилей не требуются. При двух профилях сначала подготавливаются оба агрегата, затем объединённый непустой набор предметов проверяется одним вызовом `TutoringSubjectQuery`, после чего выполняются `insert`. Конкурентный повтор создания дополнительно защищает уникальность `userId` в БД; адаптер переводит именно конфликт этой уникальности в `ProfileAlreadyExistsException`.

`ProfileInputMapper` одинаково преобразует регистрационные данные и команды обновления в проверенные TeacherProfile/StudentProfile. Он не обращается к хранилищу и переводит ожидаемые ошибки Domain в `InvalidProfileDataException` с типом профиля и именем поля. Синтаксис `photoUrl` проверяется преобразованием в URI; ограничения схемы и абсолютности отслеживаются в реестре решений.

`createInitialProfiles` требует хотя бы один из двух `Optional`; при двух ролях создаёт оба профиля атомарно. Внешний `RegistrationWorkflow` создаёт Identity.User и согласует выбранные роли с данными профилей; Tutoring проверяет лишь существование соответствующих ролей. Внешний `RoleOnboardingWorkflow` сначала добавляет вторую роль через Identity и вызывает одну типизированную команду Tutoring без даты рождения. Ошибка Tutoring откатывает и создание пользователя/роли во внешнем workflow. У двух профилей одного пользователя почта и предметы независимы.

Первоначальный `contactEmail` сохраняется как указанный пользователем контактный адрес без подтверждения. Публичный результат содержит идентификаторы созданных профилей, но не HTTP DTO.

## 5. Контактный email профиля

Контактный адрес обновляется вместе с остальными полями через `UpdateTeacherProfileUseCase` или `UpdateStudentProfileUseCase`. Сервис проверяет формат через `ProfileEmail`, вызывает `changeContactEmail` и сохраняет профиль. Тот же нормализованный адрес не меняет поле email, но остальные поля команды обновляются как обычно. Отдельных use case, команды и HTTP-запроса смены профильной почты нет. Подтверждение владения, pending-адрес, токены, квота и отправка письма для профильного адреса отсутствуют.

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

`HandleAccountEmailVerifiedService` после проверки текущего подтверждённого account email привязывает ожидающие приглашения в той же транзакции. Под общей email-блокировкой он загружает действующие `PENDING` на адрес и привязывает только записи без адресата; тот же userId — no-op, другой userId не назначается. Если после смены account email обнаружено самоприглашение, оно пропускается и остаётся `PENDING` до истечения. Ни роль, ни профиль, ни связь не создаются.

Для незарегистрированного адресата вход через ссылку приглашения ведёт к регистрации с зафиксированным в форме email приглашения. Application не доверяет блокировке поля в браузере: результат регистрации с иным подтверждённым account email не привязывает исходное приглашение. После подтверждения нужного адреса приглашение только становится доступным этому аккаунту; принятие остаётся отдельной аутентифицированной командой и требует StudentProfile.

Отдельного `INV-06` background use case в v1 нет. `now >= expiresAt` проверяется при чтении и каждой значимой команде; только INV-01 материализует старое истёкшее приглашение перед новым. Задачи очистки профильных токенов нет, поскольку контактный email профиля не подтверждается.

## 7. Relationship

`TutoringRelationshipQuery` реализует одиночную и пакетную проверку пары; `requireAllLinked` проверяет весь набор одним запросом и возвращает все отсутствующие ID через публичную ошибку. Создание `TeacherStudent` не является отдельным пользовательским use case: им владеет принятие приглашения.

`ListTeacherStudentsUseCase` и `ListStudentTeachersUseCase` берут владельца из доверенного контекста, требуют его профиль, загружают `limit + 1` связей с keyset cursor по `createdAt DESC, otherUserId DESC`, затем одним batch-вызовом получают модели профилей участников после проверки связи. Результаты содержат `linkedAt`, вычисленный возраст и указанный контактный email. `RelationshipCursorCodec` привязывает курсор к владельцу и направлению списка. Пустая страница содержит пустой список и `nextCursor = null`. В mapper передаётся модель ответа профиля, а не доменный агрегат.

`TutoringRelationshipCommands.removeTeacherStudent` является внутренней доверенной командой внешнего `UnlinkStudentWorkflow`, а не самостоятельным REST endpoint. Workflow до удаления получает guard Scheduling для пары, обрабатывает будущие уроки и незавершённые запросы, затем вызывает Tutoring в той же транзакции. Tutoring блокирует пару и удаляет только `TeacherStudent`. Повтор с тем же `operationId` возвращает сохранённый результат; новая операция на отсутствующей паре получает `TeacherStudentNotLinkedException`. Любая ошибка откатывает Scheduling, outbox и удаление. Создание нового урока для этой пары должно пользоваться совместимым guard, иначе возможна гонка после проверки.

## 8. Identity event и общая email-блокировка

`IdentityAccountGateway` предоставляет `findVerifiedAccountById(userId)` для чтения текущей подтверждённой почты преподавателя, `findByVerifiedEmail(normalizedEmail)` для поиска адресата, проверку роли и пакетное получение возраста через публичный Identity API. Он не возвращает `Identity.User`, пароль, дату рождения или токены. Техническая недоступность Identity не превращается в бизнес-ответ «не найдено».

Инфраструктурный listener переводит Identity event в `HandleAccountEmailVerifiedCommand(eventId, userId, verifiedEmail, verifiedAt)`. В write-транзакции `HandleAccountEmailVerifiedService` дедуплицирует `eventId`, берёт `NormalizedEmailLock`, **повторно подтверждает через Identity API**, что это текущий подтверждённый account email данного userId, затем пакетно привязывает действующие приглашения на адрес и сохраняет приглашения с receipt события в той же транзакции.

Устаревшее событие о прежнем account email не привязывает приглашения; при успешно прочитанном актуальном состоянии Identity оно фиксируется как обработанный no-op. Недоступность Identity требует retry, а не такого receipt. Точный повтор `eventId` не делает изменений. Отсутствие приглашений — нормальный успешный результат. Роль/профиль ученика и `TeacherStudent` событие не создаёт. Создание приглашения и этот обработчик используют один протокол email-блокировки, чтобы не пропустить привязку при гонке.

## 9. Идемпотентность и конкуренция

Для самостоятельных команд Tutoring `CommandIdempotency.beginOrReplay(operationId, userId, operationType, payloadFingerprint, resultType)` вызывается в той же транзакции до бизнес-изменений. Новый ID возвращает `Optional.empty()`; завершённый с тем же userId, типом, нормализованным payload и resultType — прежний результат; иначе — публичный `IdempotencyConflictException`. `complete(...)` фиксирует результат и `completedAt` один раз. Нельзя завершить уже завершённую операцию. Уникальный `operationId` сериализует конкурентов; после rollback первой попытки другая может выполнить команду. Fingerprint, raw payload и токены не выводятся в ошибки и логи.

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
| `PublicProfileCursorCodec` | непрозрачный проверяемый cursor для следующей страницы публичного поиска, привязанный к виду профиля, фильтрам и дате расчёта возраста |

Порты возвращают доменные типы или внутренние проекции, не jOOQ records и не чужие entity. Batch-методы не выполняют `N+1`. SQL, индексы, физический тип блокировки и сериализация cursor относятся к Infrastructure. Для приглашения понадобится расширить публичный Notifications API/шаблон: текущий verification-only gateway недостаточен; это отдельная интеграционная работа следующего этапа.

## 11. Транзакционная матрица

| Сценарий | Граница и блокировки | Атомарный результат |
|---|---|---|
| Subject, self/summary/linked profile, списки связей и приглашений | Read-only; без `operationId` | Без скрытых записей истечения |
| Обычное обновление профиля | Локальная write; профиль `forUpdate` | Обычные поля и предметы |
| PROF-11/12/13 | Внешняя Registration/RoleOnboarding write, вызов Tutoring в существующей транзакции | Identity user/role, профили, журнал Workflows |
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
- Общая модель Teacher/Student profile view используется для собственного, публичного и связанного чтения; summary остаётся отдельной короткой моделью. Для списка связей профиль выдаётся только после проверки отношения, batch-запросы обходятся без `N+1`.
- Account email не изменяется при смене контактного адреса; новый профильный email публикуется сразу как данные, введённые пользователем.
- Событие Identity привязывает приглашения к подтверждённому account email, не изменяя профильные адреса.
- INV-04 атомарно принимает приглашение и создаёт связь; внешнее удаление сначала согласует уроки с Scheduling.
- Письмо приглашения и бизнес-изменение ставятся в общую транзакцию; SMTP после commit не меняет результат.
- Прикладной Java-код подэтапов 4.1–4.7 и целевые unit-тесты реализованы. Весь этап 5 завершён 2026-10-13: SQL/jOOQ, persistence-адаптеры, интеграции, wiring, OpenAPI и HTTP DTO реализованы и проверены.

Вместе с этим документом синхронизированы прежние спецификации: в relationship чтение профиля выполняется после проверки связи и использует общую модель ответа; событие Identity оставлено для приглашений; Subject batch error приведён к публичной `UnknownSubjectsException`; invitation дополнен INV-07/08; в profile добавлен согласованный mapper. Физическая реализация БД, точные ограничения длины полей, cursor encoding, fingerprint schema, retention и client-side optimistic version остаются следующими техническими этапами.

Утверждение включает предметно-ориентированную структуру пакетов и имена новых портов. Контактная почта профиля не имеет состояния подтверждения. Документ фиксирует прикладной слой. Реализация Infrastructure/HTTP и её проверки описаны в документе и отчёте этапа 5.


### Уточнение при подключении Presentation в этапе 5

InvitationRejectedResult, SentInvitationResult и IncomingInvitationResult передают статус строкой, чтобы HTTP не зависел от доменного InvitationStatus. Invitation queries разбирают строковый HTTP status в Application; Domain и repository-порты сохраняют enum. При snapshot-чтении respondedAt отсутствует, если на asOf приглашение ещё PENDING/EXPIRED. Поздняя привязка пропускает уже связанное приглашение, даже если email теперь подтверждён другим аккаунтом. Конкурентная вставка уже существующей пары преобразуется в ALREADY_LINKED с общим rollback.


### Статус внешних Workflows на 2026-10-13

RegistrationWorkflow, RoleOnboardingWorkflow и MeQueryFacade подключены и проверены на PostgreSQL и HTTP в этапах 6–7. Пользователь отложил UnlinkStudentWorkflow, публичный unlink и StudentCardQueryFacade со статистикой до Scheduling. Описанный выше путь обработки уроков остаётся требованием будущей интеграции, а не реализованным MVP-сценарием.
