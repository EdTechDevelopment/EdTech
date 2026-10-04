# Tutoring: полная архитектура модуля

## 1. Статус и назначение

 Пути пакетов приведены относительно `io.github.edtechdevelopment`. Документ описывает требуемое поведение, а не утверждает, что классы, миграции и HTTP-контроллеры уже существуют.

### Текущий статус реализации

На 2026-10-03 в backend добавлены публичные Java-контракты Tutoring; Domain, Application, Infrastructure, HTTP-контроллеры и Flyway-таблицы модуля ещё не созданы. Identity и Notifications имеют собственный MVP-код, но не все публичные контракты, необходимые этой архитектуре. До реализации межмодульных workflows текущий account-only вход регистрации не является окончательной регистрацией с учебными профилями.


## 2. Источники истины и канонические решения

Для Tutoring этот документ является единой точкой входа. Подробные аргументы, отдельные сценарии и полные описания классов остаются в `TUTORING_STAGE_2_PUBLIC_API.md`, `TUTORING_STAGE_3_DOMAIN.md`, `TUTORING_STAGE_4_APPLICATION.md` и `TUTORING_STAGE_5_INFRASTRUCTURE_AND_PRESENTATION.md`. При расхождении действует более позднее прямое решение пользователя, зафиксированное здесь. HTTP/OpenAPI и код должны быть синхронизированы до объявления соответствующего сценария реализованным.

| Использовать | Не использовать |
|---|---|
| `TEACHER` и `STUDENT` | `TUTOR` или дополнительные роли без отдельного решения |
| Два независимых `TeacherProfile` / `StudentProfile` | Наследование от `Identity.User` или общий `Profile`-агрегат |
| `Identity.User.birthDate` только в Identity; возраст вычисляется пакетно | Копию даты рождения или сохранённый возраст в профилях |
| Возраст и указанные контакты в активном публичном профиле | Раскрытие точной даты рождения вне данных аккаунта владельца |
| `contactEmail` профиля отдельно от account email | Автоматическую замену account email профильным адресом |
| Предметы преподавателя как специализацию | Запрет вести урок вне `TeacherProfile.subjectCodes` |
| Приглашение как предложение связи | Автоматическое принятие при отправке или подтверждении email |
| Публичный `tutoring.api` для других модулей | Импорт чужим модулем внутреннего Domain/Application/Persistence Tutoring |

TeacherProfile обязан иметь минимум один предмет. StudentProfile может иметь пустой набор. Оба профиля одного пользователя независимы: их имя, предметы и контактная почта могут различаться. Контактный адрес проверяется только на формат и не считается подтверждённым.

## 3. Ответственность и границы

Tutoring владеет:

- справочником учебных предметов;
- учебными профилями преподавателя и ученика без даты рождения и их контактами;
- приглашениями ученика;
- подтверждённой парой `TeacherStudent`;
- собственными записями идемпотентности команд и обработки событий Identity;
- чтением self, public и summary-представлений; запрос списка связей сначала проверяет отношение.

Tutoring не владеет аккаунтом, ролями, account email, паролем, JWT или статусом пользователя — это Identity. Он не владеет уроками, расписанием и обработкой будущих уроков — это Scheduling. SMTP выполняет Notifications. Tutoring не добавляет или удаляет роли, не создаёт `Identity.User` и не обращается к таблицам других модулей.

Межмодульный инвариант обеспечивает Workflows:

```text
TEACHER ∈ Identity.User.roles ⇔ TeacherProfile существует
STUDENT ∈ Identity.User.roles ⇔ StudentProfile существует
при регистрации создан хотя бы один заполненный профиль
```

Identity хранит единую `birthDate` и показывает её владельцу в данных аккаунта. Тут же Identity вычисляет возраст для чтения профилей по набору userId и дате запроса. Registration/RoleOnboarding workflow не передаёт дату рождения в Tutoring, поэтому синхронизировать копии не требуется.

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

Публичные команды `TutoringProfileCreationCommands` и `TutoringRelationshipCommands` доступны только утверждённым workflows; self-view — только `MeQueryFacade`. Это закрепляется архитектурными тестами. Пользовательские сервисы получают `actorUserId` от доверенной границы, не из тела HTTP-запроса.

## 5. Итоговая структура пакетов

Пакеты организованы по предметной области. Подробные классы каждого слоя описаны в документах этапов 2–5.

```text
tutoring
├── api
│   ├── query                         Profile, Subject, Relationship
│   ├── command.profile               создание профилей из Workflows
│   ├── command.relationship          удаление связи из Workflows
│   ├── model.profile                 input, self, publicview, summary
│   └── exception                     публичные ошибки
├── presentation                      subject, profile, invitation, relationship, error
├── application                       subject, profile, invitation, relationship,
│                                     integration.identity, idempotency, email
├── domain                            subject, profile, invitation, relationship
└── infrastructure                    persistence, integration, cursor, configuration
```

В API нет дублирующих внутренних port.in для доверенных команд. В Domain профили не хранят дату рождения, возраст или состояние подтверждения контактного email. Infrastructure не содержит таблиц и задач подтверждения профильной почты. Generated jOOQ records остаются внутренними техническими типами.

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
    Optional<PublicStudentProfileView> findLinkedStudentProfile(
        UUID teacherUserId, UUID studentUserId
    );
    Optional<PublicTeacherProfileView> findLinkedTeacherProfile(
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

`findTeacherProfile`/`findStudentProfile` выдают полные self-view только `MeQueryFacade`. Публичные `findPublicTeacherProfile`/`findPublicStudentProfile` и поиск профилей доступны без связи и без login, но только для профилей активных пользователей Identity. Self и public-view содержат вычисленный Identity возраст, `displayName`, `contactDetails`, выбранные `subjectCodes`, фото, контактный email и соответствующие поля преподавателя; точную дату рождения не содержат. Контактный адрес указан пользователем и не подтверждает владение им. Summary содержит лишь `userId` и `displayName`; пустой batch summary возвращает пустую карту без SQL. `requireAllExist` и `requireAllLinked` проверяют наборы одним запросом и возвращают все отсутствующие коды/ID. Предмет урока проверяется по справочнику, не по специализации преподавателя; выбранные предметы пользователя читаются из его профиля.

Linked lookup сперва проверяет `TeacherStudent`: отсутствие связи даёт `Optional.empty()` для операции, требующей отношения, но не запрещает отдельное публичное чтение профиля. Есть связь, но нет профиля — ошибка целостности. Linked-метод использует те же публичные профильные данные, включая возраст и указанный контактный email.

### 6.2 Доверенные command API

```java
public interface TutoringProfileCreationCommands {
    InitialProfilesCreatedResult createInitialProfiles(
        CreateInitialProfilesCommand command
    );
    ProfileCreatedResult createTeacherProfile(CreateTeacherProfileCommand command);
    ProfileCreatedResult createStudentProfile(CreateStudentProfileCommand command);
}

public record CreateInitialProfilesCommand(
    UUID userId,
    Optional<TeacherProfileData> teacherProfile,
    Optional<StudentProfileData> studentProfile
) {}

public record CreateTeacherProfileCommand(
    UUID userId, TeacherProfileData profile
) {}

public record CreateStudentProfileCommand(
    UUID userId, StudentProfileData profile
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

`TeacherProfileData`: `displayName`, `contactEmail`, `contactDetails`, непустые `subjectCodes`, nullable `description`, `education`, `experienceYears`, `city`, `photoUrl`. `StudentProfileData`: те же общие данные без teacher-полей; `subjectCodes` может быть пустым. `userId` передаётся на уровне доверенной команды; дата рождения, raw password и роли в профильные модели не входят.

Self-view каждого профиля содержит его `userId`, вычисленный возраст, обычные поля и указанный контактный email. Public-view активного профиля доступен всем без связи: `userId`, возраст, `displayName`, `contactDetails`, `subjectCodes`, `contactEmail`, nullable `photoUrl`; у преподавателя также публичны `description`, `education`, `experienceYears`, `city`. Teacher/Student summary содержат только `userId`/`displayName`. Методы для списка связей проверяют связь и возвращают те же публичные модели; сама связь не служит разрешением на чтение профиля. Точная дата рождения доступна владельцу только через данные аккаунта Identity.

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
| `PUT /profiles/{type}/contact-email` | Владелец профиля | `204`, новый адрес сохраняется после проверки формата |
| `POST /invitations` | Активный TEACHER с TeacherProfile | `201` `{invitationId,expiresAt}` |
| `GET /invitations/sent` | Владелец TeacherProfile | `200` cursor-страница |
| `GET /invitations/incoming` | Привязанный аутентифицированный пользователь | `200` cursor-страница |
| `POST /invitations/{id}/accept` | Адресат с StudentProfile | `200` минимальный результат |
| `POST /invitations/{id}/reject` | Адресат; StudentProfile не нужен | `200` минимальный результат |
| `GET /relationships/students` | Владелец TeacherProfile | `200` linked-список |
| `GET /relationships/teachers` | Владелец StudentProfile | `200` linked-список |

Публичный поиск принимает необязательные `subjectCode`, `minAge`, `maxAge`, `cursor`, `limit`; возраст вычисляет Identity из своей даты рождения на дату запроса по UTC; Tutoring не хранит его. Список содержит только активные профили Identity, сортируется по `createdAt DESC, userId DESC`, имеет формат `{items,nextCursor}` и предел 1…100 (по умолчанию 50). Курсор подписан и привязан к виду профиля, фильтрам и дате расчёта возраста. Предметный фильтр сверяет выбранные предметы именно в профиле; справочник нужен для выбора и проверки допустимости кода. Нельзя показывать ожидающий подтверждения, приостановленный или деактивированный аккаунт. Для страниц нужен пакетный Identity API получения возраста и проверки активности, чтобы избежать отдельного запроса на каждого пользователя и пустых страниц после фильтрации.

`POST /invitations` и оба ответа требуют заголовок `Idempotency-Key: UUID`. Отсутствующий/некорректный ключ — `400`. Запросы не принимают `actorUserId` или клиентский email при ответе. Получение входящих и отказ доступны до student-onboarding; принятие — после появления StudentProfile. Изменяющего `GET` для принятия приглашения нет.

### 7.2 DTO и ответы

Teacher PUT содержит `displayName`, `contactDetails`, непустой `subjectCodes`, nullable `description`, `education`, `experienceYears`, `city`, `photoUrl`. Student PUT содержит общие `displayName`/`contactDetails`/`subjectCodes` и nullable `photoUrl`; пустые subjectCodes разрешены. Коллекции не могут быть `null`. Необязательное поле, опущенное в полном PUT или переданное как `null`, очищается. контактный email меняется отдельным PUT, а даты рождения в профиле нет.

Email-change DTO содержит только `newEmail`. Успешное изменение сразу возвращает `204`; писем, токенов и состояния ожидания у контактного адреса нет.

Списки приглашений принимают status-фильтр, cursor и limit (по умолчанию 50, диапазон 1…100), возвращают `{items,nextCursor}` без общего total. Отправленный элемент содержит адрес цели, эффективный статус и даты, но не `studentUserId` и не факт регистрации. Входящий элемент содержит имя преподавателя из batch summary. Списки связей возвращают `linkedAt`, публичные данные связанных профилей и cursor. Связь требуется для самих списков и операций с уроками, но не для публичного просмотра профиля.

### 7.3 Ошибки и доступ

Форма ответа совместима с `{code,message,fieldErrors,requestId}`, но Tutoring не импортирует `identity.presentation`. `400`: валидация, cursor, ключ, неизвестные предметы и неверный формат контактного email. `401`: отсутствующая обязательная аутентификация. `403`: действие запрещено, включая создание приглашения без роли TEACHER. `404`: собственный отсутствующий профиль или единый ответ для отсутствующего/чужого приглашения. `409`: конфликт приглашения/состояния, отсутствие StudentProfile при принятии, конфликт operationId. `500/503`: нарушение целостности либо техническая недоступность.

Ни response, ни логи ошибок не включают полный fingerprint/payload и лишние персональные данные. Пустой справочник предметов — внутренняя ошибка `SubjectNotFoundException`, не успешный пустой список.

## 8. Application Tutoring

Application координирует сценарии профилей, предметов, приглашений и связей. Внутренние use cases обслуживают HTTP; доверенные TutoringProfileCreationCommands и TutoringRelationshipCommands реализуются сервисами Application без дублирующего port.in. Через output ports сервисы сохраняют собственные агрегаты и обращаются к публичным API Identity и Notifications.

Identity отвечает за точную дату рождения. Для self/public-view и поиска Tutoring запрашивает возраст через пакетный IdentityQuery.findAgesByIds(userIds, asOf); публичное чтение отдельно проверяет ACTIVE-статус. Профильные таблицы не содержат дату рождения или возраст. Поиск читает кандидатов по предмету и порядку createdAt/userId, пакетно проверяет возраст и активность, затем добирает страницу до limit + 1. Дата расчёта возраста UTC закрепляется в курсоре.

Обновление обычных полей полностью заменяет их, проверяет предметы пакетно и блокирует строку профиля. Контактный email меняется отдельным use case и сразу сохраняется после проверки формата. При создании профилей Workflows открывает общую транзакцию с Identity; Tutoring проверяет существование соответствующих ролей и не получает дату рождения. Приглашения, отношения, идемпотентность и обработка Identity-события описаны подробно в TUTORING_STAGE_4_APPLICATION.md.

## 9. Domain Tutoring

TeacherProfile и StudentProfile — независимые агрегаты с ID userId. Оба хранят displayName, один contactEmail, contactDetails, SubjectSelection и необязательное фото. Teacher дополнительно хранит описание, образование, опыт и город. Teacher требует минимум один предмет, Student допускает пустой набор. Оба профиля не хранят birthDate, age или состояние подтверждения контактной почты. Изменение контактного email проверяет формат и сразу заменяет адрес.

Subject — справочник предметов. StudentInvitation — самостоятельный агрегат приглашения со сроком и состояниями PENDING, ACCEPTED, REJECTED, EXPIRED. TeacherStudent — самостоятельная направленная пара пользователей, создаваемая при принятии приглашения. Domain не обращается к Identity, Notifications, SQL или HTTP. Полная модель и инварианты описаны в TUTORING_STAGE_3_DOMAIN.md.

## 10. Infrastructure Tutoring

Infrastructure реализует output ports Application: jOOQ/Flyway для собственных таблиц Tutoring, адаптеры Identity и Notifications, подписанные курсоры и транзакционные блокировки. Адаптер Identity получает возраст и статус пакетно, проверяет роли и ищет адресатов приглашений по подтверждённому account email. Адаптер Notifications ставит письма приглашений в durable delivery; профильные письма не отправляются. Identity event используется только для привязки ожидающих приглашений.

## 11. PostgreSQL и миграции Tutoring

В v1 запланированы девять таблиц: каталог предметов; два профиля; два набора выбранных предметов; приглашения; пары TeacherStudent; операции идемпотентности; receipt обработанных Identity-событий. В профильных таблицах нет birth_date, age, pending_contact_email и contact_email_verified_at. Таблицы подтверждения контактной почты также нет. SQL, индексы и ограничения подробно описаны в TUTORING_STAGE_5_INFRASTRUCTURE_AND_PRESENTATION.md.

## 12. Внешние Workflows и фасады

Tutoring не владеет всей транзакцией регистрации, ролями Identity и расписанием. Эти процессы координируются отдельными верхнеуровневыми workflow/facade, вызывающими публичные API модулей.

| Координатор | Последовательность и граница |
|---|---|
| `RegistrationWorkflow` | Валидирует выбранные роли (минимум STUDENT или TEACHER) и соответствующие заполненные профильные данные; в общей транзакции создаёт Identity.User и ровно требуемые Tutoring-профили без копирования даты рождения. Ошибка любого шага откатывает оба модуля. |
| `RoleOnboardingWorkflow` | Для второй роли получает данные нового профиля, добавляет роль в Identity и создаёт ровно один профиль в той же транзакции без передачи даты рождения. Первый профиль не меняется. |
| `UnlinkStudentWorkflow` | В общей транзакции применяет Scheduling guard, обрабатывает уже существующие уроки по политике Scheduling и только затем вызывает `TutoringRelationshipCommands.removeTeacherStudent`. Создание нового урока использует совместимый guard/lock. |
| `MeQueryFacade` | Объединяет Identity и Tutoring self-данные для `/me`; отсутствие профиля при имеющейся роли трактует как нарушение инварианта, не как нормальное «пусто». |
| `StudentCardQueryFacade` | Собирает карточку ученика из публичных данных Tutoring; статистику его уроков показывает только преподавателю с подтверждённой связью через отдельный доступ Scheduling. BirthDate берёт из профиля Tutoring. |

Доверенные команды Tutoring требуют активной общей PostgreSQL-транзакции (`MANDATORY`) и не доступны прямым HTTP-контроллером. Для сквозной атомарности модули используют один DataSource/transaction manager. Если в будущем граница транзакции изменится, эти workflow нужно перепроектировать как распределённые процессы, а не молча сохранить прежние гарантии. Повтор регистрации и добавления роли целиком обрабатывает Workflows; независимые команды Tutoring используют собственный `operationId`.

## 13. Контактный email и событие Identity

Account email Identity и contact email обоих профилей независимы. Tutoring проверяет формат контактного адреса, сразу сохраняет его и показывает в активном публичном профиле как сведения, введённые владельцем. Контактный адрес не подтверждает личность и не используется для входа или поиска получателя приглашения.

Событие подтверждения account email Identity нужно Tutoring для привязки ожидающих приглашений. Обработчик повторно проверяет актуальный подтверждённый account email через публичный API Identity, привязывает действующие приглашения и атомарно сохраняет receipt события. Контактный email профилей событие не меняет.

## 14. Приглашения, страницы и связь

`INV-01`: учитель с активной TEACHER-ролью и TeacherProfile указывает email. Application нормализует его, запрещает самоприглашение, проверяет отсутствие подтверждённой связи и действующего PENDING. Известного пользователя ищет лишь по **подтверждённому** account email Identity; неизвестный адрес допустим. При создании известный recipient привязывается сразу; для неизвестного ID остаётся пустым. Новое приглашение живёт ровно 30 суток, уведомление ставится в очередь атомарно. Создание не требует StudentProfile и не позволяет учителю выбрать recipient ID.

Привязка позже происходит при актуальном подтверждении account email в Identity. `attachStudent` устанавливает ID и `attachedAt`, но не принимает приглашение и не назначает роль. Она идемпотентна для того же ID; переназначение другому пользователю запрещено. Истёкшие приглашения не привязываются. Отдельной периодической INV-06 нет: эффективный статус вычисляется при чтении, а сохранённый `EXPIRED` материализуется при повторном создании.

Отправленные/входящие страницы сортируются по `created_at DESC, id DESC`. Первый запрос фиксирует `asOf`, который подписанный cursor переносит на следующие страницы. Фильтр эффективного статуса применяется **до** `limit + 1`; исторический статус на `asOf` использует `respondedAt` и `expiresAt`, не позднейшие изменения. Входящие требуют `student_user_id = actorUserId` и `attached_at <= asOf`. Отправленный элемент не раскрывает recipient ID/факт регистрации; входящий обогащается именем учителя batch summary. Limit — 50 по умолчанию, 1…100; нет total count.

`INV-04` (accept) и `INV-05` (reject) проверяют привязанного адресата, `PENDING` и строгую границу `now < expiresAt`. Reject доступен до появления StudentProfile, accept — только после него. На ответном шаге TeacherProfile отправителя не перечитывается: действительное приглашение уже установило отправителя. Accept меняет статус и создаёт одну `TeacherStudent` пару атомарно. Конкурентные ответы сериализуются блокировкой и PK; второй действительный переход невозможен. Отсутствующее и чужое приглашение имеют одинаковый внешний `404`.

Relationship-списки сортируются по `created_at DESC, other_user_id DESC` и дают подписанный cursor без `asOf` — живой список, не исторический snapshot. Проверка пары ограничивает доступ к списку связей и урокам; данные самого активного профиля, включая дату рождения и подтверждённые контакты, публичны. Удаление связи не входит в эти read use cases и происходит только по `UnlinkStudentWorkflow`.

## 15. Транзакции и идемпотентность

RegistrationWorkflow и RoleOnboardingWorkflow создают аккаунт/роль Identity и соответствующие профили Tutoring в одной PostgreSQL-транзакции. UnlinkStudentWorkflow сначала согласует уроки с Scheduling и затем удаляет связь Tutoring в общей транзакции. Самостоятельные write-команды Tutoring используют operationId; обработка Identity-события сохраняет receipt по eventId. Для создания приглашения и его поздней привязки используется одна транзакционная блокировка нормализованного account email.

## 16. Безопасность и приватность

Точная дата рождения хранится только в Identity и показывается владельцу в данных аккаунта. Профили показывают вычисленный возраст. Указанный пользователем контактный email публичен для активного профиля, но не считается подтверждённым и не используется для входа или поиска получателя приглашения. Наличие связи, приглашения и данные уроков доступны только по правилам соответствующих сценариев. Actor userId берётся из доверенного principal, а не из HTTP-тела.

## 17. Проверки и порядок реализации

Архитектурные тесты должны подтверждать, что другие модули используют только tutoring.api, Domain не зависит от Spring/HTTP/SQL, Infrastructure не читает чужие таблицы, а self-view выдаётся только владельцу через MeQueryFacade. При реализации требуются проверки общей транзакции Workflows, профилей, поиска по возрасту без N+1, приглашений, связи и идемпотентности. На текущем шаге реализованы только публичные Java-контракты Tutoring.

## 18. Связанные материалы

Подробные контракты и сценарии находятся в TUTORING_STAGE_2_PUBLIC_API.md, TUTORING_STAGE_3_DOMAIN.md, TUTORING_STAGE_4_APPLICATION.md, TUTORING_STAGE_5_INFRASTRUCTURE_AND_PRESENTATION.md и TUTORING_APPLICATION_SCENARIOS.md. Текущее решение: дата рождения только в Identity, профильный контактный email без подтверждения, активный публичный профиль с возрастом и контактами доступен без подтверждённой связи.
