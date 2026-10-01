# Tutoring — этап 2. Публичный Java API

Статус: утверждённая спецификация этапа 2
Дата создания: 2026-09-15
Дата утверждения пользователем: 2026-09-16
Актуальное решение: каждый учебный профиль хранит `birthDate`; `BirthDateVisibilityView` удалён. Public-view активного профиля возвращает дату и подтверждённые контакты без проверки `TeacherStudent`.
Синхронизация от 2026-09-24: отсутствие профиля при существующей связи является нарушением целостности, а не `Optional.empty()`.

## 1. Назначение и исходные решения

Этот документ определяет **только межмодульный Java API** `tutoring.api`: его потребителей, интерфейсы, входные команды, возвращаемые модели, ошибки, транзакционные условия. Frontend использует REST API, а не описанные здесь Java-интерфейсы. Внутренние use cases, контроллеры, domain aggregates, persistence и точный API Notifications проектируются на следующих этапах.

Исходные документы: `tutoring-stage-1-boundaries-and-use-cases.md`, `IDENTITY_ARCHITECTURE.md`, `BACKEND_ARCHITECTURE.md` и текущие HTTP-контракты проекта. Этап 1 фиксирует бизнес-границы; эта спецификация уточняет названия и формы публичного API. Упоминания единого `TutoringQuery`, `TutoringManagement` и generic `createProfile(...)` являются прежними вариантами, не параллельными интерфейсами.

Принятые принципы:

- Query API разделён по возможностям: профиль, предмет, отношение.
- Отсутствие результата в `find*` и `exists` нормально; `require*` сигнализирует типизированным исключением.
- Read-модели сценарные: self, summary и linked. Полный профиль не передаётся потребителю, которому достаточно имени.
- Предметы профиля описывают специализацию и **не** ограничивают предмет урока.
- `Identity.User.birthDate` остаётся источником истины; команды создания передают дату в Tutoring, self- и linked-view профилей могут содержать её согласно фиксированному правилу доступа.
- Регистрация и добавление второй роли используют одну PostgreSQL-транзакцию, открываемую Workflows.
- Исходящих публичных событий Tutoring в первой версии нет; доставку приглашения и подтверждения профильного email Tutoring инициирует через public command Notifications.

## 2. Потребители и границы доступа

| Потребитель | Возможности Tutoring | Не получает |
|---|---|---|
| Scheduling | Профиль преподавателя существует; связи; существование предмета; пакетные краткие имена | Полные профили, pending email, repositories |
| `RegistrationWorkflow` | Создание выбранных первоначальных профилей | Domain aggregates Tutoring |
| `RoleOnboardingWorkflow` | Создание профиля второй роли | Прямое изменение роли внутри Tutoring |
| `UnlinkStudentWorkflow` | Проверка и удаление связи после обработки уроков | Прямой доступ к таблице `teacher_students` |
| `MeQueryFacade` | Полные self-views двух профилей | Доменную модель профиля |
| `StudentCardQueryFacade` | Публичный StudentProfile с `birthDate`; статистика уроков после проверки связи | Статистика без подтверждённой связи |
| Другие утверждённые query facades | Пакетные summary и linked views согласно конкретному сценарию | Неподтверждённый email другого пользователя |

Полные `TeacherProfileView` и `StudentProfileView` доступны **только** утверждённому `MeQueryFacade`. Он берёт `userId` из доверенного principal и запрашивает профиль того же пользователя. Публичный Java-метод сам по себе не содержит HTTP-principal; ограничение импорта закрепляется ArchUnit, а равенство `principal.userId == userId` проверяется фасадом. Остальные модули не должны импортировать self-view типы и методы.

Linked-методы выполняют проверку `TeacherStudent` **внутри Tutoring до выдачи данных**. Нет связи — `Optional.empty()` без раскрытия чужого профиля. Если связь существует, но соответствующий профиль отсутствует, это нарушение целостности, а не нормальный пустой результат. Переданное `userId` не является достаточным основанием для доступа к контактам.

## 3. Итоговая структура `tutoring.api`

```text
tutoring.api
├── query
│   ├── TutoringProfileQuery
│   ├── TutoringSubjectQuery
│   └── TutoringRelationshipQuery
├── command
│   ├── registration
│   │   ├── TutoringRegistrationCommands
│   │   ├── CreateInitialProfilesCommand
│   │   ├── CreateTeacherProfileCommand
│   │   ├── CreateStudentProfileCommand
│   │   ├── InitialProfilesCreatedResult
│   │   └── ProfileCreatedResult
│   └── relationship
│       ├── TutoringRelationshipCommands
│       ├── RemoveTeacherStudentCommand
│       └── TeacherStudentRemovalResult
├── model
│   └── profile
│       ├── ProfileTypeView
│       ├── input
│       │   ├── TeacherProfileData
│       │   └── StudentProfileData
│       ├── self
│       │   ├── TeacherProfileView
│       │   └── StudentProfileView
│       ├── summary
│       │   ├── TeacherProfileSummary
│       │   └── StudentProfileSummary
│       └── linked
│           ├── LinkedStudentProfileView
│           └── LinkedTeacherProfileView
└── exception
    ├── ProfileNotFoundException
    ├── ProfileAlreadyExistsException
    ├── InvalidProfileDataException
    ├── UnknownSubjectsException
    ├── TeacherStudentNotLinkedException
    └── IdempotencyConflictException
```

Разделение следует жизненному циклу контрактов: доверенные команды и их результаты живут рядом по бизнес-сценарию, а профильные модели разделены по аудитории и степени раскрытия данных. Пакеты `input`, `self`, `summary` и `linked` содержат реальные типы, а не пустые заготовки. `query` не дробится: в нём всего три узких интерфейса.

Пакет `tutoring.api.event` в v1 **не создаётся**: у Tutoring пока нет утверждённого внешнего подписчика на факт изменения его данных. Входящий `identity.api.event.AccountEmailVerifiedEvent` остаётся типом Identity.

### Готовые Ответственность пакетов

| Package | Description |
|---|---|
| `tutoring.api` | Стабильная публичная граница Tutoring для других backend-модулей. Содержит только необходимые межмодульные чтения, доверенные команды, неизменяемые модели и ошибки. Не содержит HTTP DTO, security principal, domain aggregates или persistence-типы. |
| `tutoring.api.query` | Синхронные read-only контракты для проверки профиля, предметов и подтверждённых связей. Реализуются application-сервисами Tutoring и вызываются другими модулями без доступа к внутренним repositories. Не изменяют состояние и не выдают персональные данные сверх модели конкретного сценария. |
| `tutoring.api.command` | Группирует доверенные межмодульные команды по бизнес-сценарию. Сам пакет не содержит классов: регистрация/onboarding и удаление связи имеют отдельные контракты. Не является пользовательской HTTP-границей. |
| `tutoring.api.command.registration` | Контракт Workflows для первоначального создания одного или двух профилей и создания профиля второй роли. Содержит интерфейс, команды и их минимальные результаты; реализации присоединяются к общей транзакции. Не добавляет роли Identity и не принимает пароль. |
| `tutoring.api.command.relationship` | Контракт UnlinkStudentWorkflow на удаление принадлежащей Tutoring подтверждённой связи. Содержит интерфейс, команду и результат одной операции. Не координирует уроки Scheduling и не доступен frontend напрямую. |
| `tutoring.api.model` | Группирует только публичные неизменяемые модели по предметной области. Сам пакет не содержит классов; он не является складом команд, результатов, HTTP DTO или domain aggregates. |
| `tutoring.api.model.profile` | Содержит общий публичный `ProfileTypeView`, используемый командами, результатами и ошибками. Не содержит `UserRole` Identity или состояние domain aggregate. |
| `tutoring.api.model.profile.input` | Данные первоначального создания TeacherProfile и StudentProfile, передаваемые доверенными workflows в Tutoring. Использует общие профильные enum-типы и преобразуется в локальные value objects внутри модуля. Не содержит account data, raw password, `userId` или HTTP request DTO. |
| `tutoring.api.model.profile.self` | Полные представления существующих профилей для владельца через MeQueryFacade. Содержат current/pending contact email и `birthDate`, но не другие account data. Не импортируются Scheduling и не используются для чужого linked-ответа. |
| `tutoring.api.model.profile.summary` | Минимальные пакетные сведения `userId` и `displayName` для отображения преподавателя/ученика в других модулях. Не раскрывает контакты, возраст, предметы или внутреннее состояние профиля. |
| `tutoring.api.model.profile.publicview` | Публичные представления активных профилей: `birthDate`, обычные сведения, `contactDetails`, предметы и подтверждённый профильный email. Связь не требуется. |
| `tutoring.api.model.profile.linked` | Представления для списков подтверждённых связей TeacherStudent. Их профильные поля публичны и без связи; pending email отсутствует. |
| `tutoring.api.exception` | Типизированные нарушения публичного Java-контракта. Несут машинно читаемый контекст, но не HTTP status и не `ApiError`. Отображение на транспортные ошибки выполняет presentation/composer владельца HTTP endpoint. |

## 4. Query API

### 4.1 `TutoringProfileQuery`

```java
package tutoring.api.query;

public interface TutoringProfileQuery {
    Optional<TeacherProfileView> findTeacherProfile(UUID userId);
    Optional<StudentProfileView> findStudentProfile(UUID userId);
    Optional<PublicTeacherProfileView> findPublicTeacherProfile(UUID userId);
    Optional<PublicStudentProfileView> findPublicStudentProfile(UUID userId);

    void requireTeacherProfile(UUID teacherUserId);

    Map<UUID, TeacherProfileSummary> findTeacherSummaries(
        Set<UUID> teacherUserIds
    );
    Map<UUID, StudentProfileSummary> findStudentSummaries(
        Set<UUID> studentUserIds
    );

    Optional<LinkedStudentProfileView> findLinkedStudentProfile(
        UUID teacherUserId,
        UUID studentUserId
    );
    Optional<LinkedTeacherProfileView> findLinkedTeacherProfile(
        UUID studentUserId,
        UUID teacherUserId
    );
}
```

`findTeacherProfile`/`findStudentProfile` возвращают self-view существующего профиля и используются только `MeQueryFacade`. При отсутствии профиля возвращают `Optional.empty()`. Фасад сверяет отсутствие с ролями Identity: роль без профиля является нарушением межмодульного инварианта, а не обычным onboarding-состоянием.

`findPublicTeacherProfile`/`findPublicStudentProfile` возвращают профиль без связи и без login только для активного пользователя Identity. Public-view содержит дату рождения, имя профиля, описание и прочие обычные поля, `contactDetails`, предметы, а `contactEmail` — только если он подтверждён. Pending email, неподтверждённый email и account email отсутствуют. Публичный HTTP-поиск использует отдельный application use case с пакетной проверкой статуса Identity и фильтрами предмета/возраста.

`requireTeacherProfile` используется Scheduling при создании урока. Он проверяет **наличие профиля**, но не проверяет, входит ли предмет урока в `TeacherProfile.subjectCodes`. При отсутствии — `ProfileNotFoundException(TEACHER, teacherUserId)`.

`findTeacherSummaries`/`findStudentSummaries` являются пакетными. `Summary` — сокращённая модель с `userId` и `displayName`, а не полный профиль. Возвращаемая `Map` содержит запись для каждого найденного профиля; отсутствующие ID в неё не входят. Метод не выполняет отдельный запрос к Identity для каждой записи и не возвращает contact email. Если вызывающему модулю необходимо проверить статус пользователя, он делает это через `IdentityQuery`.

`findLinkedStudentProfile` и `findLinkedTeacherProfile` проверяют подтверждённую связь для сценария отношений. Их профильные поля соответствуют публичному представлению и не требуют связи сами по себе. Они возвращают только подтверждённый `contactEmail`; если он не подтверждён, поле равно `null`. Account email не подставляется вместо неподтверждённого профильного адреса.

При отсутствии связи linked-методы возвращают `Optional.empty()`; отдельные public-методы продолжают работать. Отсутствующий профиль при существующей связи означает нарушение целостности и не маскируется под `Optional.empty()`.

Типы результата импортируются из `tutoring.api.model.profile.self`, `.publicview`, `.summary` и `.linked`; enum `ProfileTypeView` для ошибки отсутствия профиля — из `tutoring.api.model.profile`. Query-пакет не владеет моделями и не переэкспортирует HTTP DTO.

### 4.2 `TutoringSubjectQuery`

```java
package tutoring.api.query;

public interface TutoringSubjectQuery {
    boolean exists(String subjectCode);
    void requireAllExist(Set<String> subjectCodes);
}
```

`exists` — обычная проверка одного кода. `requireAllExist` выполняет **одну пакетную проверку** и при неизвестных кодах выбрасывает `UnknownSubjectsException` с полным набором неизвестных кодов. Оба метода проверяют только справочник `Subject`; `teacherUserId` не принимают.

HTTP `GET /subjects` обслуживается внутренним use case Tutoring. Межмодульный API не возвращает весь справочник, поскольку Scheduling нужен только факт существования кода.

### 4.3 `TutoringRelationshipQuery`

```java
package tutoring.api.query;

public interface TutoringRelationshipQuery {
    boolean areLinked(UUID teacherUserId, UUID studentUserId);

    void requireLinked(UUID teacherUserId, UUID studentUserId);

    void requireAllLinked(
        UUID teacherUserId,
        Set<UUID> studentUserIds
    );
}
```

`areLinked` нужен для ветвления без исключения. `requireLinked` и `requireAllLinked` выбрасывают `TeacherStudentNotLinkedException`. Последнее исключение содержит все отсутствующие `studentUserIds` и не заменяется циклом отдельных вызовов. Наличие связи не доказывает активность аккаунта: актуальный статус/роль, если нужны сценарию, проверяются через Identity API.

### 4.4 Общие правила query

- Все методы read-only: не вызывают `expire()` и не материализуют статус приглашения скрытой записью.
- Для `UUID` синтаксис уже проверен на входной HTTP-границе; `null` в Java API запрещён.
- `subjectCode` передаётся в согласованном нормализованном формате справочника; `null`, пустая или синтаксически некорректная строка — ошибка вызывающего кода.
- Пустой batch-набор для `findTeacherSummaries`/`findStudentSummaries` возвращает пустую `Map` без запроса к БД (позднее уточнение PROF-03/04). Для остальных batch-методов пустой набор, а для всех методов `null`-набор или его `null`-элемент — `IllegalArgumentException`.
- `find*` не возвращает `null`; `Map`, `Set`, `List` не содержат `null`.
- `Optional.empty()` для linked lookup означает отсутствие связи; при существующей связи отсутствие профиля — внутренняя ошибка целостности без раскрытия чужих данных.

## 5. Command API

### 5.1 `TutoringRegistrationCommands`

```java
package tutoring.api.command.registration;

public interface TutoringRegistrationCommands {
    InitialProfilesCreatedResult createInitialProfiles(
        CreateInitialProfilesCommand command
    );
    ProfileCreatedResult createTeacherProfile(
        CreateTeacherProfileCommand command
    );
    ProfileCreatedResult createStudentProfile(
        CreateStudentProfileCommand command
    );
}

public record CreateInitialProfilesCommand(
    UUID operationId,
    UUID userId,
    LocalDate birthDate,
    Optional<TeacherProfileData> teacherProfile,
    Optional<StudentProfileData> studentProfile
) {}

public record CreateTeacherProfileCommand(
    UUID operationId,
    UUID userId,
    LocalDate birthDate,
    TeacherProfileData profile
) {}

public record CreateStudentProfileCommand(
    UUID operationId,
    UUID userId,
    LocalDate birthDate,
    StudentProfileData profile
) {}

public record InitialProfilesCreatedResult(
    UUID operationId,
    UUID userId,
    Set<ProfileTypeView> createdProfiles
) {}

public record ProfileCreatedResult(
    UUID operationId,
    UUID userId,
    ProfileTypeView profileType
) {}
```

`TeacherProfileData` и `StudentProfileData` импортируются из `tutoring.api.model.profile.input`; `ProfileTypeView` — из `tutoring.api.model.profile`. Результаты регистрации/onboarding принадлежат `command.registration`, поскольку их жизненный цикл связан с этими командами, а не со всеми моделями Tutoring.

`RegistrationWorkflow` создаёт `operationId`, проверяет точное соответствие `roles ↔ profiles`, вызывает Identity и затем `createInitialProfiles`. Он передаёт в команду `birthDate`, сохранённую в созданном `Identity.User`. В команде Tutoring **нет ролей и raw password**. Минимум один из двух `Optional` содержит данные; оба пустых — `InvalidProfileDataException`. Tutoring проверяет свой инвариант: через публичный `IdentityQuery` убеждается, что соответствующая роль существует у переданного `userId` в той же транзакции, но не меняет роль и не проверяет общий набор ролей повторно.

`RoleOnboardingWorkflow` после `IdentityRoleCommands.addRole(...)` получает существующий `Identity.User.birthDate` и вызывает **ровно один** типизированный метод `createTeacherProfile` или `createStudentProfile`. Generic `createProfile(ProfileType, Object)` не создаётся. Каждый метод проверяет отсутствие профиля, роль через `identity.api`, обязательность `birthDate`, валидность собственных данных и существование предметов.

Результат команды содержит только минимальные идентификаторы. Регистрация после commit отвечает `202 VerificationPendingResponse` без токенов; составной `MeResponse` собирается `MeQueryFacade` для последующего `GET /me`. При добавлении второй роли workflow после commit возвращает `201 MeResponse`. HTTP DTO не возвращается из Java API Tutoring.

### 5.2 `TutoringRelationshipCommands`

```java
package tutoring.api.command.relationship;

public interface TutoringRelationshipCommands {
    TeacherStudentRemovalResult removeTeacherStudent(
        RemoveTeacherStudentCommand command
    );
}

public record RemoveTeacherStudentCommand(
    UUID operationId,
    UUID teacherUserId,
    UUID studentUserId
) {}

public record TeacherStudentRemovalResult(
    UUID operationId,
    UUID teacherUserId,
    UUID studentUserId
) {}
```

Метод вызывается только `UnlinkStudentWorkflow` **после** `SchedulingManagement.processFutureLessonsForUnlink(...)` в той же общей транзакции. Tutoring повторно проверяет наличие пары и удаляет только принадлежащую ему связь. Он не вызывает Scheduling и не читает таблицы уроков. Самостоятельный REST endpoint, направленный прямо на эту команду, запрещён.

### 5.3 Транзакция и идемпотентность

- Реализации всех четырёх межмодульных write-методов работают с `Propagation.MANDATORY`: вызов без внешней транзакции завершается ошибкой конфигурации/контракта до изменения данных. Workflows открывает внешнюю транзакцию единым `PlatformTransactionManager` и datasource.
- Запись об обработанном `operationId`, типе команды, fingerprint входных данных и возвращённом результате принадлежит Tutoring и фиксируется вместе с изменениями Tutoring.
- Для Tutoring один `operationId` обозначает **одну** command-операцию. Повтор того же метода с эквивалентным нормализованным payload возвращает прежний результат. Другой метод или другой payload с тем же ID — `IdempotencyConflictException`.
- Нормализация email и предметов выполняется до вычисления fingerprint; порядок элементов наборов не меняет смысл payload. Raw password в fingerprint Tutoring не входит.
- Уникальные ограничения профиля по `userId` и связи по `(teacherUserId, studentUserId)` являются последней защитой конкурентных запросов.
- Для повторного `removeTeacherStudent` с **тем же** `operationId` возвращается сохранённый результат. Новая операция на уже удалённую пару получает `TeacherStudentNotLinkedException`.
- Одинаковый `operationId` гарантирует идемпотентность **Java-команд**. Повтор HTTP-запроса не получает эту гарантию автоматически: workflow создаст новый `operationId`, если не сможет распознать повтор исходного запроса. При последующей синхронизации HTTP-контрактов регистрация, role-onboarding и отвязка должны принимать стабильный клиентский `Idempotency-Key`; Workflows хранит отображение `(сценарий, пользователь или регистрационный контекст, ключ) → operationId` и при повторе использует прежний ID. Текущий OpenAPI такого заголовка не определяет и до синхронизации не обещает сквозную HTTP-идемпотентность.

## 6. Public models: атрибуты и правила

Типы ниже являются Java records/enums публичного API. Поля `String` с email/subject code/photo URL — межмодульный формат; преобразование в локальные value objects `ProfileEmail`, `SubjectCode`, `URI` выполняет application/domain Tutoring. Скалярное `null` допустимо **только** там, где поле явно помечено `?`. Коллекции defensive-copy (`List.copyOf`, `Set.copyOf`, `Map.copyOf`) и никогда не содержат `null`. Значения enum не дублируют внутренние enum-типы domain.

### 6.1 Self-view — только `MeQueryFacade`

```java
package tutoring.api.model.profile.self;

public record TeacherProfileView(
    UUID userId,
    LocalDate birthDate,
    String displayName,
    String contactEmail,
    String pendingContactEmail,          // ?
    Instant contactEmailVerifiedAt,      // ?
    List<String> contactDetails,
    Set<String> subjectCodes,
    String description,                  // ?
    String education,                    // ?
    Integer experienceYears,             // ?
    String city,                         // ?
    String photoUrl                      // ?
) {}

public record StudentProfileView(
    UUID userId,
    LocalDate birthDate,
    String displayName,
    String contactEmail,
    String pendingContactEmail,          // ?
    Instant contactEmailVerifiedAt,      // ?
    List<String> contactDetails,
    Set<String> subjectCodes,
    String photoUrl                      // ?
) {}
```

Self-view показывает владельцу `birthDate`, текущий и pending профильный email, даже если email ещё не подтверждён. Это не даёт права использовать неподтверждённый адрес для уведомлений другому человеку.

### 6.2 Summary — краткие пакетные сведения

```java
package tutoring.api.model.profile.summary;

public record TeacherProfileSummary(UUID userId, String displayName) {}
public record StudentProfileSummary(UUID userId, String displayName) {}
```

`Summary` не содержит фото, предметы, контакты, роль, статус аккаунта или возраст. При создании и чтении урока Scheduling может пакетно получить имена, но данные о статусе пользователя получает отдельно через Identity, если они необходимы.

### 6.3 Публичный профиль и linked-view

```java
package tutoring.api.model.profile.publicview;

public record PublicStudentProfileView(
    UUID userId, LocalDate birthDate, String displayName,
    String contactEmail, // nullable; только подтверждённый
    List<String> contactDetails, Set<String> subjectCodes, String photoUrl
) {}

public record PublicTeacherProfileView(
    UUID userId, LocalDate birthDate, String displayName,
    String contactEmail, // nullable; только подтверждённый
    List<String> contactDetails, Set<String> subjectCodes,
    String description, String education, Integer experienceYears,
    String city, String photoUrl
) {}
```

Публичное чтение доступно любому посетителю для активного профиля. Выбранные предметы читаются из профиля, возраст вычисляется из `birthDate` при поиске. Nullable teacher-поля и `photoUrl` сохраняют ту же nullable-семантику, что self-view. Краткий `Summary` остаётся отдельным внутренним типом.

Linked-view ниже используется там, где само действие зависит от подтверждённой связи; он не ограничивает публичное чтение профиля. При формировании linked-списка не следует урезать публичные `contactDetails`, `education`, `experienceYears` и `city`.

```java
package tutoring.api.model.profile.linked;

public record LinkedStudentProfileView(
    UUID userId,
    LocalDate birthDate,
    String displayName,
    String contactEmail,                 // ?; только подтверждённый
    List<String> contactDetails,
    Set<String> subjectCodes,
    String photoUrl                      // ?
) {}

public record LinkedTeacherProfileView(
    UUID userId,
    LocalDate birthDate,
    String displayName,
    String contactEmail,                 // ?; только подтверждённый
    List<String> contactDetails,
    Set<String> subjectCodes,
    String description,                  // ?
    String education,                    // ?
    Integer experienceYears,             // ?
    String city,                         // ?
    String photoUrl                      // ?
) {}
```

Оба linked-view создаются после проверки `TeacherStudent`, потому что используются для списка отношений. Публичное чтение тех же профильных данных, включая точную `birthDate`, не требует связи. Настройки видимости нет.

### 6.4 Входные профильные данные и общие enum-типы

```java
package tutoring.api.model.profile.input;

public record TeacherProfileData(
    String displayName,
    String contactEmail,
    List<String> contactDetails,
    Set<String> subjectCodes,
    String description,                  // ?
    String education,                    // ?
    Integer experienceYears,             // ?
    String city,                         // ?
    String photoUrl                      // ?
) {}

public record StudentProfileData(
    String displayName,
    String contactEmail,
    List<String> contactDetails,
    Set<String> subjectCodes,
    String photoUrl                      // ?
) {}
```

Общий enum типа профиля находится на уровне `tutoring.api.model.profile`, потому что используется командами, результатами и исключениями:

```java
package tutoring.api.model.profile;

public enum ProfileTypeView { TEACHER, STUDENT }
```

Минимум один предмет обязателен только в `TeacherProfileData`; `StudentProfileData` допускает пустой набор. Предметы описывают профиль и не являются разрешением вести урок.

В `TeacherProfileData` и `StudentProfileData` нет `userId` и `birthDate`: общие значения принадлежат доверенной registration/onboarding-команде. В профильных данных также нет ролей, пароля, account email, `pendingContactEmail` и verification token. Tutoring получает account email через `IdentityQuery`, чтобы решить, можно ли подтвердить совпадающий профильный адрес после account-email verification.

## 7. Ошибки публичного Java API

Все ожидаемые нарушения представлены типизированными unchecked exceptions. Публичное исключение не импортирует domain exception и не содержит HTTP status, локализованного сообщения или `ApiError`.

| Exception | Машинно читаемые поля | Возникает при |
|---|---|---|
| `ProfileNotFoundException` | `profileType`, `userId` | `requireTeacherProfile` не нашёл профиль |
| `ProfileAlreadyExistsException` | `profileType`, `userId` | Новая команда пытается создать уже существующий профиль |
| `InvalidProfileDataException` | `profileType?`, `invalidFields: Set<String>` | Нарушен профильный контракт, в том числе оба initial profiles отсутствуют |
| `UnknownSubjectsException` | `unknownCodes: Set<String>` | `requireAllExist` либо создание профиля получили неизвестные предметы |
| `TeacherStudentNotLinkedException` | `teacherUserId`, `missingStudentUserIds: Set<UUID>` | `requireLinked`, `requireAllLinked` или новая команда удаления не нашли связь |
| `IdempotencyConflictException` | `operationId` | Повтор command с изменённым payload/типом |

Если отсутствие нормально, применяется `Optional.empty()` или `false`, а не исключение. `IllegalArgumentException` означает ошибку Java-вызова (`null`, пустой batch, невалидная форма кода) и не является бизнес-ошибкой.

Ошибки авторизации, неактивного аккаунта и обязательной роли принадлежат Identity/Workflows либо внутреннему application-сценарию Tutoring. Внешний HTTP-слой самостоятельно отображает публичные исключения на утверждённые REST error codes; прямое включение HTTP status в Java API запрещено.

## 8. Ответственность элементов публичного API

Описание каждого элемента фиксирует ответственность, взаимодействие и ограничения.

### 8.1 Интерфейсы

| Элемент | Ответственность |
| --- | --- |
| `TutoringProfileQuery` | Предоставляет self-, public-, summary- и linked-представления профилей и проверку наличия профиля преподавателя. Public-view активного пользователя доступен без связи; linked-view проверяет связь из-за назначения операции. Не отдаёт domain aggregate или неподтверждённые контакты. |
| `TutoringSubjectQuery` | Проверяет существование одного или нескольких кодов в принадлежащем Tutoring справочнике Subject. Вызывается Scheduling и другими согласованными потребителями; реализуется application-сервисом Tutoring. Не принимает преподавателя и не превращает профильные предметы в допуск к уроку. |
| `TutoringRelationshipQuery` | Проверяет одну или набор подтверждённых связей преподавателя с учениками. Используется Scheduling и Workflows; реализуется application-сервисом с доступом к собственному relationship repository. Не возвращает `TeacherStudent` и не управляет уроками. |
| `TutoringRegistrationCommands` | Принимает доверенные команды первоначального создания профилей и создания профиля второй роли. Вызывается Registration/RoleOnboarding workflows и реализуется Tutoring command service в их общей транзакции. Не добавляет роли Identity, не принимает пароль и не является REST controller. |
| `TutoringRelationshipCommands` | Удаляет принадлежащую Tutoring связь по запросу `UnlinkStudentWorkflow` после успешной обработки будущих уроков. Реализация присоединяется к внешней транзакции и использует собственный repository port. Не вызывает Scheduling и не открывает прямой пользовательский endpoint. |

### 8.2 Commands, views и results

| Элемент | Ответственность |
| --- | --- |
| `CreateInitialProfilesCommand` | Передаёт Tutoring `operationId`, созданный `userId` и данные одного или двух выбранных профилей. Формируется RegistrationWorkflow и обрабатывается `TutoringRegistrationCommands`. Не содержит ролей, account data или raw password. |
| `CreateTeacherProfileCommand` | Передаёт создание TeacherProfile при добавлении роли TEACHER с тем же `operationId`, что у Identity. Формируется RoleOnboardingWorkflow и обрабатывается Tutoring. Не добавляет роль самостоятельно. |
| `CreateStudentProfileCommand` | Передаёт создание StudentProfile и обязательную birthDate при добавлении роли STUDENT с тем же `operationId`, что у Identity. Формируется RoleOnboardingWorkflow и обрабатывается Tutoring. Не изменяет аккаунт. |
| `RemoveTeacherStudentCommand` | Определяет пару связи и `operationId` для удаления после обработки уроков. Формируется только UnlinkStudentWorkflow и обрабатывается Tutoring. Не описывает способ изменения уроков. |
| `TeacherProfileView` | Возвращает владельцу полное состояние TeacherProfile, включая birthDate и current/pending контактный email. Получается только MeQueryFacade для текущего principal. Не содержит пароль или доменные объекты. |
| `StudentProfileView` | Возвращает владельцу полное состояние StudentProfile, включая birthDate и current/pending контактный email. Получается только MeQueryFacade для текущего principal. Не содержит пароль или доменные объекты. |
| `TeacherProfileSummary` | Даёт только `userId` и `displayName` для пакетного отображения преподавателей в других модулях. Возвращается `TutoringProfileQuery`. Не раскрывает контакты, профильные подробности и статус Identity. |
| `StudentProfileSummary` | Даёт только `userId` и `displayName` для пакетного отображения учеников в других модулях. Возвращается `TutoringProfileQuery`. Не раскрывает контакты, возраст и профильные подробности. |
| `LinkedStudentProfileView` | Возвращает преподавателю birthDate и разрешённые данные связанного ученика только после проверки TeacherStudent. Не содержит pending или неподтверждённый email. |
| `LinkedTeacherProfileView` | Возвращает ученику birthDate и разрешённые данные связанного преподавателя только после проверки TeacherStudent. Не раскрывает pending email. |
| `TeacherProfileData` | Передаёт Tutoring необходимые для первичного создания профильные данные преподавателя. Формируется workflow из валидированного HTTP DTO и переводится application/domain в локальные типы. Не является domain aggregate или HTTP request DTO. |
| `StudentProfileData` | Передаёт Tutoring ролевые данные StudentProfile. Общая birthDate передаётся на уровне доверенной команды. Не содержит данные входа в аккаунт. |
| `InitialProfilesCreatedResult` | Подтверждает обработанную registration-команду и перечисляет созданные типы профилей. Возвращается TutoringRegistrationCommands и хранится для идемпотентного повтора. Не является HTTP `MeResponse`. |
| `ProfileCreatedResult` | Подтверждает создание одного профиля второй роли и обработанный `operationId`. Возвращается `TutoringRegistrationCommands` в RoleOnboardingWorkflow. Не выдаёт полный профиль или новый JWT. |
| `TeacherStudentRemovalResult` | Подтверждает удаление указанной связи и обработанный `operationId`. Возвращается UnlinkStudentWorkflow, в том числе при повторе той же операции. Не сообщает изменения уроков Scheduling. |
| `ProfileTypeView` | Кодирует только тип создаваемого или отсутствующего учебного профиля. Используется результатами и исключениями Tutoring API. Не заменяет `Identity.UserRole` и не управляет авторизацией. |

### 8.3 Исключения

| Элемент | Ответственность |
| --- | --- |
| `ProfileNotFoundException` | Сообщает вызывающему backend-сценарию, что обязательный профиль не существует. Создаётся реализацией `requireTeacherProfile`, содержит тип и userId. Не содержит HTTP 404 или данные Identity. |
| `ProfileAlreadyExistsException` | Сообщает о попытке создать второй профиль одного типа для userId. Возникает в command service Tutoring после проверки или DB constraint. Не превращается напрямую в транспортный response. |
| `InvalidProfileDataException` | Сообщает о нарушениях входного профильного контракта, включая отсутствие обеих моделей в initial-команде. Создаётся application/domain translation. Не содержит локализованных сообщений HTTP-валидации. |
| `UnknownSubjectsException` | Перечисляет неизвестные коды предметов при пакетной проверке или создании профиля. Возникает через Subject repository Tutoring. Не проверяет специализацию конкретного преподавателя. |
| `TeacherStudentNotLinkedException` | Перечисляет одну или несколько отсутствующих связей преподавателя с учениками. Возникает в relationship query/command. Не раскрывает relationship aggregate или данные уроков. |
| `IdempotencyConflictException` | Указывает, что ранее обработанный `operationId` повторён с другим типом команды или payload. Возникает в command service Tutoring до нового изменения данных. Не содержит секретов, полный payload или HTTP 409. |

## 9. Зависимости и запреты
Запрещены:

```text
tutoring.api → tutoring.application / tutoring.domain / tutoring.infrastructure
Scheduling   → tutoring.domain / tutoring.infrastructure / таблицы Tutoring
Workflows    → внутренние repositories или domain Identity/Tutoring/Scheduling
Tutoring     → scheduling.*
tutoring.api → HTTP DTO / Spring Security principal / jOOQ records
Tutoring     → Identity.User или таблицы Identity
```

## 10. Матрица трассировки сценариев

| Сценарий | Публичный контракт Tutoring | Модель/результат | Ожидаемая отрицательная ветка |
|---|---|---|---|
| TUT-14: одна связь | `TutoringRelationshipQuery.areLinked` / `requireLinked` | `boolean` / `void` | `false` / `TeacherStudentNotLinkedException` |
| TUT-15: набор связей | `TutoringRelationshipQuery.requireAllLinked` | `void` | `TeacherStudentNotLinkedException` со всеми отсутствующими ID |
| TUT-16: предмет существует | `TutoringSubjectQuery.exists` / `requireAllExist` | `boolean` / `void` | `false` / `UnknownSubjectsException` |
| TUT-17: межмодульный профиль | `TutoringProfileQuery.find*Profile`, `find*Summaries`, `findLinked*` | self/summary/linked views | `Optional.empty()` или отсутствие ключа в Map |
| TUT-18: удаление связи | `TutoringRelationshipCommands.removeTeacherStudent` | `TeacherStudentRemovalResult` | `TeacherStudentNotLinkedException`, `IdempotencyConflictException` |
| TUT-19: смена профильного email | **Внутренний use case Tutoring**, не `tutoring.api` | application result/HTTP 202 | Невалидный email, ownership, неверный профиль |
| TUT-20: подтверждение профильного email | **Внутренний use case Tutoring**, не `tutoring.api` | application result/HTTP 204 | Недействительный/использованный verification token |
| TUT-21: account email подтверждён | Входящий `identity.api.event.AccountEmailVerifiedEvent` | Нет исходящей public модели Tutoring | Повторный `eventId` идемпотентен |
| TUT-22: первоначальный профиль | `TutoringRegistrationCommands.createInitialProfiles` | `InitialProfilesCreatedResult` | `InvalidProfileDataException`, `ProfileAlreadyExistsException`, `UnknownSubjectsException`, `IdempotencyConflictException` |
| WF-01: регистрация | `createInitialProfiles` после `IdentityRegistrationCommands.createPendingUser` | `InitialProfilesCreatedResult` | Общий rollback при ошибке любого модуля |
| WF-02: вторая роль | `createTeacherProfile` **или** `createStudentProfile` после `IdentityRoleCommands.addRole` | `ProfileCreatedResult` | Общий rollback, повтор с тем же operationId безопасен |
| WF-03: отвязка | `requireLinked` → Scheduling API → `removeTeacherStudent` | `TeacherStudentRemovalResult` | Общий rollback, связь остаётся при ошибке уроков |
| QF-01: карточка ученика | `findPublicStudentProfile`; связь проверяется для статистики уроков | `PublicStudentProfileView.birthDate` и разрешённая статистика | Профиль доступен без связи, статистика — только при связи |
| QF-02: `/me` | `findTeacherProfile` / `findStudentProfile` | self-views | Отсутствие обязательного профиля — инвариантная ошибка |

TUT-01…TUT-13, кроме межмодульных проверок, обслуживаются внутренними application use cases и presentation Tutoring. Их HTTP DTO не являются публичными Java-моделями `tutoring.api`.

## 11. Проверки и критерии приёмки

### Contract tests

- Public-типы размещены в пакетах из раздела 3; в корневых `tutoring.api.command` и `tutoring.api.model` нет классов, результаты команд не находятся среди профильных моделей.
- Сигнатуры интерфейсов и public records совпадают со спецификацией; нет generic `TutoringQuery`, generic `createProfile` и методов `teachesSubject`/`requireTeacherCanTeach`.
- `find*` корректно возвращают `Optional.empty()`/отсутствующие ключи Map; `require*` возвращают перечисленные типизированные исключения.
- Batch-вызовы не превращаются в N+1; при неизвестных предметах/несвязанных учениках возвращаются полные наборы ошибок.
- `Map`/`Set`/`List` immutable defensive-copy и без `null`; некорректный Java-вызов не изменяет данные.

### Privacy и integration tests

- Self-view содержит pending email только для владельца через `MeQueryFacade`.
- Linked view не выдаёт pending или неподтверждённый contact email и не появляется без `TeacherStudent`.
- Self-view возвращает birthDate владельцу; public-view активного профиля возвращает её любому посетителю без связи; summary остаётся кратким и не содержит дату.
- Публичный поиск фильтрует предметы по профилю и возраст по его birthDate, не выдаёт неактивные аккаунты Identity и не допускает N+1-проверки статуса. Неподтверждённая и pending почта не выходят в публичный ответ.
- Scheduling может создать урок по существующему предмету вне `TeacherProfile.subjectCodes`, если прочие проверки выполнены.
- Повтор `AccountEmailVerifiedEvent` не меняет результат: связывание приглашений и подтверждение совпадающего email идемпотентны.
- Доставка приглашения/profile-email verification сохраняет delivery request в транзакции, SMTP не запускается до commit.

### Transaction и architecture tests

- Вызов command service без внешней транзакции с `MANDATORY` не записывает данные.
- Ошибка Tutoring при регистрации/onboarding откатывает Identity; ошибка Scheduling при отвязке сохраняет связь.
- Повтор Java-команды с тем же `operationId`/payload возвращает прежний результат; изменённый payload вызывает `IdempotencyConflictException`; конкурентные запросы не создают дубли. Сквозной HTTP-повтор проверяется после добавления `Idempotency-Key` в транспортный контракт.
- Spring Modulith/ArchUnit запрещают импорты внутренних пакетов и чужих repositories/tables; Scheduling импортирует только `tutoring.api`; self-view используется только утверждённой query facade.
- Публичный `tutoring.api` не зависит от Spring, HTTP DTO, domain, jOOQ, SMTP и Scheduling.

Этап 2 утверждён пользователем 2026-09-16. Зафиксированы границы публичного Java API, структура пакетов, интерфейсы, команды, модели, ошибки и транзакционные условия, описанные в этом документе. Спецификация является согласованной основой для следующих этапов проектирования Tutoring.

Перечисленные проверки остаются требованиями к будущей реализации; утверждение архитектуры не означает, что код реализован или тесты выполнены. изменение Git-проекта и синхронизация OpenAPI выполняются по отдельному запросу.
