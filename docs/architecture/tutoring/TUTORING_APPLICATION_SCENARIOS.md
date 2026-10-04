# Tutoring Application — единая спецификация сценариев

Статус: проектная спецификация утверждённых сценариев, не Java-реализация. Объединяет шесть предметных блоков Application. Для технических решений Infrastructure и Presentation применяется [итоговая архитектура](TUTORING_ARCHITECTURE.md) и [этап 5](TUTORING_STAGE_5_INFRASTRUCTURE_AND_PRESENTATION.md). Более поздние согласованные правила имеют приоритет над историческими формулировками в сценариях.

Состав документа:

1. Subject — каталог и проверки предметов.
2. Profile — чтение, изменение и создание профилей.
3. Idempotency — повторы команд и событий.
4. Integration — взаимодействие с Identity.
5. Invitation — создание, списки, ответы и привязка.
6. Relationship — связь, списки и отвязка.

## 1. Subject

### Статус и назначение

Документ фиксирует утверждённый проект блока `tutoring.application.subject`.

Блок выполняет три сценария:

1. Получение каталога предметов.
2. Проверка существования одного предмета.
3. Проверка существования набора предметов.

Блок работает без проверки пользователя и не управляет справочником через пользовательские команды.

### Границы ответственности

`application.subject` отвечает за:

- чтение справочника предметов;
- проверку одного `subjectCode`;
- пакетную проверку набора кодов;
- формирование ошибки со всеми неизвестными кодами.

`application.subject` не отвечает за:

- проверку роли или активности пользователя;
- создание и изменение профиля;
- обязательность выбора предметов;
- проверку специализации преподавателя при создании урока;
- пользовательское создание, изменение и удаление предметов.

Минимум один предмет является инвариантом `TeacherProfile`. `StudentProfile` может содержать пустой набор предметов.

### Структура блока

```text
tutoring.application.subject
├── port
│   ├── in
│   │   └── GetSubjectsUseCase
│   └── out
│       └── SubjectRepository
├── model
│   └── result
│       └── SubjectResult
├── service
│   ├── GetSubjectsService
│   └── TutoringSubjectQueryService
├── mapper
│   └── SubjectResultMapper
└── exception
    ├── InvalidSubjectCodeException
    └── SubjectNotFoundException
```

`model.query` и `model.command` не создаются: получение каталога не принимает параметры, а пользовательское изменение справочника в v1 отсутствует.

### Сценарий 1 — получение каталога предметов

#### Точка входа

```java
public interface GetSubjectsUseCase {

    List<SubjectResult> getSubjects();
}
```

Входные данные отсутствуют. Проверка пользователя, роли и профиля не выполняется.

Результат:

```java
public record SubjectResult(
    String subjectCode,
    String name
) {}
```

Domain Value Object `SubjectCode` наружу из Application не передаётся.

#### Последовательность

```text
1. Клиент вызывает GetSubjectsUseCase.getSubjects().
2. GetSubjectsService обращается к SubjectRepository.findAll().
3. Repository возвращает предметы.
4. Если справочник пуст — выбрасывается SubjectNotFoundException.
5. SubjectResultMapper преобразует каждый Subject в SubjectResult.
6. Результат упорядочивается по name ASC, затем subjectCode ASC.
7. Возвращается неизменяемый List<SubjectResult>.
```

Используется `List`, а не `Set`, поскольку клиенту требуется стабильный порядок отображения. Уникальность `subjectCode` обеспечивает справочник и ограничение базы данных.

Операция выполняется в read-only транзакции.

### Сценарий 2 — проверка одного предмета

#### Точка входа

Сценарий реализуется утверждённым межмодульным API:

```java
public interface TutoringSubjectQuery {

    boolean exists(String subjectCode);
}
```

#### Последовательность

```text
1. Вызывающий модуль передаёт subjectCode.
2. Проверка пользователя не выполняется.
3. Application проверяет, что код не null и не blank.
4. Строка преобразуется в Domain SubjectCode.
5. TutoringSubjectQueryService вызывает SubjectRepository.exists(...).
6. Для существующего предмета возвращается true.
7. Для корректного, но отсутствующего предмета возвращается false.
```

`null`, blank и некорректный формат приводят к `InvalidSubjectCodeException`. `SubjectNotFoundException` не используется, поскольку отсутствие является штатным результатом `false`.

### Сценарий 3 — проверка набора предметов

#### Точка входа

```java
public interface TutoringSubjectQuery {

    void requireAllExist(Set<String> subjectCodes);
}
```

#### Последовательность

```text
1. Вызывающий сценарий передаёт непустой Set<String>.
2. Проверка пользователя не выполняется.
3. Application проверяет сам набор и каждый его элемент.
4. Строки преобразуются в Domain SubjectCode.
5. Repository одним запросом возвращает существующие коды.
6. Application вычисляет requestedCodes - existingCodes.
7. Если разность пуста — метод успешно завершается.
8. Если разность непуста — публичный `TutoringSubjectQuery` выбрасывает `UnknownSubjectsException` со всеми отсутствующими кодами. `SubjectNotFoundException` остаётся внутренней ошибкой сценария каталога.
```

Неизвестные коды включаются в ошибку в стабильном отсортированном порядке. Проверка не останавливается на первом отсутствующем предмете.

Публичный `requireAllExist` принимает только непустой набор. Для ученика с пустым выбором предметов вызывающий профильный сценарий пропускает эту проверку.

### Repository-порт

```java
public interface SubjectRepository {

    List<Subject> findAll();

    boolean exists(SubjectCode subjectCode);

    Set<SubjectCode> findExistingCodes(
        Set<SubjectCode> subjectCodes
    );
}
```

Правила:

- `findAll` возвращает все предметы справочника;
- `exists` не выбрасывает ошибку при отсутствии предмета;
- `findExistingCodes` выполняет один пакетный запрос;
- repository не вычисляет отсутствующие коды;
- repository не содержит SQL-, jOOQ- или HTTP-типы;
- пустой внутренний набор не отправляется в repository.

### Использование другими сценариями

Изменение профиля преподавателя:

```text
requireAllExist(subjectCodes)
→ TeacherProfile.changeSubjects(...)
```

`TeacherProfile` самостоятельно запрещает пустой набор.

Изменение профиля ученика:

```text
если набор пуст — изменить профиль без вызова requireAllExist
если набор непуст — requireAllExist(subjectCodes), затем изменить профиль
```

Scheduling может использовать `TutoringSubjectQuery` для проверки существования предмета урока. При этом предметы `TeacherProfile` описывают специализацию и не ограничивают предмет создаваемого урока.

### Управление справочником

В v1 отсутствуют:

- `CreateSubjectUseCase`;
- `UpdateSubjectUseCase`;
- `DeleteSubjectUseCase`;
- административные HTTP endpoints.

Справочник наполняется и изменяется миграциями базы данных. Стабильный `subjectCode` после создания не изменяется.

### Критерии завершённости

- Каталог возвращает `subjectCode` и `name`.
- Domain `SubjectCode` не выходит из Application.
- Каталог имеет стабильный порядок.
- Получение каталога не требует пользователя.
- Одиночная проверка возвращает `boolean`.
- Набор проверяется одним запросом.
- Ошибка содержит все неизвестные коды.
- Tutoring не проверяет роль при работе со справочником.
- Пустой набор ученика не передаётся в `requireAllExist`.
- Предметы профиля преподавателя не ограничивают предмет урока.
- Пользовательский CRUD справочника отсутствует.

## 2. Profile

### Граница ответственности

TeacherProfile и StudentProfile — независимые агрегаты Tutoring с ключом userId. Они хранят учебные данные и один контактный email каждый. Роли, account email, точная дата рождения и статус аккаунта принадлежат Identity. В профилях не сохраняется возраст: Identity вычисляет его из своей даты рождения на указанную дату.

### Чтение профилей

ProfileQueryService реализует TutoringProfileQuery. Собственный профиль выдаётся только через MeQueryFacade, который сверяет principal.userId с запрошенным ID. Self-view содержит возраст и контактный email, но не дату рождения аккаунта. Отсутствие профиля при наличии соответствующей роли является нарушением межмодульного инварианта.

Public-view активного пользователя доступен без связи и login. Tutoring читает свои профильные поля, пакетно получает возраст и активность из Identity, затем формирует PublicTeacherProfileView или PublicStudentProfileView. Указанный пользователем contactEmail показывается без статуса подтверждения. Для списков подтверждённых связей relationship сначала проверяет пары TeacherStudent и затем использует те же public-view. Summary содержит только userId и displayName; пустой набор ID не вызывает SQL.

Публичный поиск фильтрует предметы по профилям и сортирует кандидатов по createdAt DESC, userId DESC. Возраст и ACTIVE-статус проверяются пакетно через Identity без N+1; при узком возрастном фильтре Tutoring добирает следующие порции кандидатов до limit + 1 либо конца набора. Курсор сохраняет UTC-дату расчёта возраста, чтобы следующие страницы использовали ту же дату.

### Изменение профиля

UpdateTeacherProfileUseCase и UpdateStudentProfileUseCase получают доверенный actorUserId, проверяют все subjectCodes одним запросом, блокируют собственный профиль, полностью заменяют обычные поля и выбор предметов, затем сохраняют агрегат. Teacher требует минимум один предмет, Student допускает пустой набор. Эти команды не меняют контактный email: для него есть ChangeProfileEmailUseCase.

ChangeProfileEmailUseCase получает доверенный actorUserId, тип профиля и newEmail. Сервис проверяет формат, загружает собственный профиль с блокировкой и сразу сохраняет новый контактный адрес. Повтор того же нормализованного адреса — no-op. Контактный адрес не подтверждается письмом, не имеет pending-состояния и не используется как адрес входа или ключ поиска приглашений.

### Создание из доверенных workflows

TutoringProfileCreationCommands предоставляет createInitialProfiles, createTeacherProfile и createStudentProfile. RegistrationWorkflow создаёт Identity.User и соответствующие профили в одной транзакции; RoleOnboardingWorkflow добавляет роль Identity и создаёт один новый профиль в той же транзакции. Tutoring проверяет наличие нужной роли через публичный API Identity, валидность данных и предметы. Команды содержат userId и профильные данные, но не birthDate, роли или пароль. Ошибка любого шага откатывает всю транзакцию.
## 3. Idempotency

### Ответственность и границы

`tutoring.application.idempotency` обеспечивает повторяемость самостоятельных команд Tutoring по `operationId` и однократное применение входящего `eventId`. Регистрация и добавление роли используют журнал Workflows. Idempotency Tutoring не генерирует ID, не определяет бизнес-результат и не подменяет уникальные ограничения БД.

Запись о команде принадлежит Tutoring и сохраняется в одной транзакции с бизнес-изменениями и результатом. При ошибке вся транзакция откатывается: ни завершённой записи, ни бизнес-изменений не остаётся.

### Структура и контракты

```text
tutoring.application.idempotency
├── port.in
│   └── CommandIdempotency
├── port.out
│   ├── CommandOperationRepository
│   └── ProcessedIdentityEventRepository
├── service
│   ├── CommandIdempotencyService
│   └── IdentityEventDeduplicationService
└── model
    ├── OperationType
    ├── PayloadFingerprint
    ├── BeginDecision<R> = Proceed | Replay<R>
    ├── CommandOperation
    └── ProcessedIdentityEvent
```

Типы в `model` — внутренние application-модели; они не являются Domain Aggregate Root или типами публичного API. `OperationType` различает конкретные команды. Внешний `IdempotencyConflictException` остаётся в `tutoring.api.exception`.

Логический контракт:

```java
<R> BeginDecision<R> beginOrReplay(
    UUID operationId,
    UUID userId,
    OperationType operationType,
    PayloadFingerprint payloadFingerprint,
    Class<R> resultType
);

<R> void complete(
    UUID operationId,
    UUID userId,
    OperationType operationType,
    PayloadFingerprint payloadFingerprint,
    R result
);
```

`resultType` нужен для безопасного возврата ранее сохранённого результата; его точное представление в БД ещё не выбирается. Параметры `complete` позволяют сверить, что завершается именно начатая операция. Для самостоятельных команд Tutoring `userId` — доверенный actor/owner ID; регистрация и добавление роли проходят через журнал Workflows.

Минимальные логические данные `CommandOperation`: `operationId` (уникальный ключ), `userId`, `operationType`, `payloadFingerprint`, сохранённый тип/схема результата, сохранённый результат и `completedAt`. Промежуточное состояние допускается только внутри незавершённой транзакции и не считается отдельным бизнес-статусом.

### IDEM-01 — начало или возврат результата

Вызывающий command-сервис до бизнес-проверок и изменений нормализует семантический payload, вычисляет fingerprint и вызывает `beginOrReplay` **в той же транзакции**, где выполнит команду.

1. Проверить обязательные `operationId`, `userId`, тип операции и fingerprint.
2. Найти или зарезервировать запись по `operationId` через `CommandOperationRepository`.
3. Если ID новый — вернуть `Proceed`; вызывающий сервис продолжает сценарий.
4. Если ID уже завершён и совпадают `userId`, `operationType`, fingerprint и ожидаемый тип результата — вернуть `Replay` с сохранённым результатом. Вызывающий сервис немедленно возвращает его без повторных бизнес-вызовов.
5. Если при том же ID различается хотя бы одно из этих значений — выбросить `IdempotencyConflictException`. Полный payload, токены и персональные данные не включать в ошибку или журнал.

Таким образом, прежние IDEM-03 (replay) и IDEM-04 (conflict) входят в IDEM-01 и не требуют отдельных публичных сценариев или дополнительных вызовов вызывающего сервиса.

Конкурентность (прежний IDEM-05) также является частью `beginOrReplay`: БД защищает уникальный `operationId`. Второй конкурентный вызов не исполняет бизнес-логику параллельно с первым. После commit первого он получает `Replay` или конфликт; после rollback первого может стать первым выполнением. Конкретный SQL-протокол (`insert-if-absent`/блокировка/повтор транзакции) определит Persistence, не полагаясь на перехват ошибки уникальности внутри уже непригодной транзакции.

### IDEM-02 — завершение команды

После успешных бизнес-изменений вызывающий сервис передаёт результат в `complete` **до commit той же транзакции**.

1. Найти зарезервированную запись и сверить ID, пользователя, тип и fingerprint.
2. Отклонить отсутствие записи, несовпадение данных или попытку завершить уже завершённую операцию.
3. Проверить соответствие результата типу данной команды.
4. Получить `completedAt` из application `Clock` и сохранить результат с отметкой завершения.
5. Общая транзакция фиксирует бизнес-данные, связанные запросы доставки и результат операции атомарно.

Повторный вызов `complete` не является способом replay: повтор команды обрабатывается только через IDEM-01.

### IDEM-06 — входящее событие

Для `AccountEmailVerifiedEvent` применяется отдельный учёт `eventId`, поскольку это не пользовательская команда с возвращаемым результатом. Обработчик в одной транзакции проверяет `eventId`, подтверждает совпадающие текущие профильные адреса, привязывает действующие приглашения и отмечает событие обработанным. Повтор обработанного `eventId` не меняет состояние; при rollback отметка исчезает вместе с изменениями. Конкурентный дубль защищён уникальностью `eventId` и тем же правилом ожидания результата первой транзакции.

`ProcessedIdentityEvent` хранит `eventId`, `eventType`, версионированный fingerprint канонического payload и время успешной обработки. Один ID с иным содержанием — конфликт. Автоматическая очистка receipts в v1 не предусмотрена. Отдельный публичный use case IDEM-06 не нужен: это внутренняя часть `HandleAccountEmailVerifiedUseCase`.

### Ошибки, проверки, зависимости

- `IdempotencyConflictException`: тот же `operationId` с другим пользователем, типом операции, fingerprint или типом результата.
- Ошибка целостности/состояния: `complete` без начатой либо для уже завершённой операции. Это дефект вызова, а не повод выполнить бизнес-команду повторно.
- Техническая ошибка хранения откатывает всю транзакцию; повтор возможен с тем же ID.
- Проверки: первый вызов, точный повтор, изменённый payload, другой тип команды, два конкурентных одинаковых запроса, rollback первого, двойное завершение, повтор `eventId`.

`service` зависит от собственных `port.out`; инфраструктура реализует репозитории. Запрещены зависимости application от jOOQ records, HTTP DTO, Spring Security principal и конкретного механизма доставки событий.

## 4. Integration

### Identity API

Tutoring вызывает только публичный API Identity. Для карточек и поиска используется IdentityQuery.findAgesByIds(userIds, asOf), возвращающий возраст существующих пользователей без точной даты рождения; для публичных ответов дополнительно проверяется ACTIVE-статус. Проверка роли при создании профиля не копирует роль в Tutoring. Приглашение ищет адресата только по подтверждённому account email Identity, а не по контактному адресу профиля.

### AccountEmailVerifiedEvent

Подтверждение account email может привязать ожидающие приглашения к пользователю. Listener переводит событие в Application-команду; сервис дедуплицирует eventId, берёт общую email-блокировку с созданием приглашения и повторно сверяет актуальный подтверждённый адрес через Identity. Затем пакетно привязывает действующие приглашения и сохраняет receipt в той же транзакции. Устаревшее событие фиксируется как успешный no-op; недоступность Identity требует retry. Контактные email профилей событие не меняет.

### Notifications

Tutoring инициирует через публичный API Notifications только письма приглашений. Заявка сохраняется в общей транзакции, а SMTP выполняется после commit. Профильный контактный email не подтверждается и писем для него нет.
## 5. Invitation

### Общие правила

`StudentInvitation` — самостоятельный Aggregate Root. Срок приглашения составляет ровно 30 суток; при `now >= expiresAt` сохранённое `PENDING` уже считается истёкшим независимо от записи статуса в БД. Принятие создаёт `TeacherStudent` в той же транзакции. Роль, профиль ученика и связь не создаются при отправке или привязке приглашения к аккаунту.

Для write-команд используется `operationId`: точный повтор возвращает сохранённый результат, иной payload с тем же ID — конфликт. Время поступает от application `Clock` и передаётся в Domain явно. Чтение приглашений не меняет сохранённый статус.

### INV-01 — создание приглашения

Вход: `operationId`, email адресата и доверенный `teacherUserId`. Активность аккаунта и роль `TEACHER` контролирует Identity/authentication boundary; Tutoring требует наличие `TeacherProfile` отправителя.

1. Нормализовать адрес и выполнить проверку/резервирование `operationId` до бизнес-изменений.
2. В write-транзакции получить общую с обработчиком `AccountEmailVerifiedEvent` блокировку нормализованного email.
3. Через публичный Identity API сравнить адрес с account email преподавателя и попытаться найти адресата только по подтверждённому account email. Совпадение с преподавателем — самоприглашение и ошибка.
4. Проверить отсутствие действующего приглашения этого преподавателя на адрес. Если старое `PENDING` уже истекло, вызвать `expire(now)` и сохранить `EXPIRED` в этой же write-транзакции перед созданием нового приглашения.
5. Если адресат найден, проверить отсутствие подтверждённой связи пары и создать приглашение с `studentUserId` и `attachedAt = createdAt`. Если аккаунт не найден, создать приглашение без обоих полей.
6. Сохранить приглашение, поставить заявку на письмо в Notifications с deduplication key `INVITATION_CREATED:{invitationId}` и завершить запись идемпотентности в той же транзакции. SMTP выполняется только после commit.
7. Вернуть `invitationId` и `expiresAt`, не раскрывая факт существования аккаунта адресата.

Известному адресату не требуется уже иметь роль `STUDENT` и `StudentProfile`: они нужны лишь перед принятием. Ошибка enqueue до commit откатывает всё создание; ошибка SMTP после commit его не отменяет. Конкурентность защищают email-блокировка, проверка действующего приглашения и ограничение хранения.

### INV-02 — список приглашений преподавателя

Вход: доверенный `teacherUserId`, необязательный фильтр по эффективному статусу, непрозрачный `cursor` и `limit`. Tutoring требует наличие `TeacherProfile`, но не обращается в Identity за ролью повторно.

1. Проверить `limit` (предлагаемое общее правило: по умолчанию 50, диапазон 1…100) и декодировать курсор, привязанный к преподавателю и фильтру.
2. Для первой страницы сохранить `asOf = now`; последующие страницы используют `asOf` курсора.
3. В read-only транзакции выбрать только приглашения этого преподавателя с `createdAt <= asOf`; восстановить статус на момент `asOf` по `respondedAt` и `expiresAt`, затем применить фильтр **до** ограничения страницы.
4. Сортировать по `createdAt DESC, invitationId DESC`, загрузить `limit + 1`, сформировать `nextCursor` по последней возвращённой записи.
5. Вернуть `invitationId`, адрес приглашения, эффективный статус, `createdAt`, `expiresAt`, `respondedAt`. `studentUserId` и факт регистрации адресата не раскрывать.

Восстановление статуса на момент `asOf`: если `respondedAt <= asOf`, вернуть сохранённый `ACCEPTED`/`REJECTED`; иначе если `expiresAt <= asOf`, вернуть `EXPIRED`; иначе `PENDING`. Это важно и для приглашения, которое было принято, отклонено либо материализовано как `EXPIRED` после открытия первой страницы: текущий сохранённый статус нельзя просто передать в `effectiveStatusAt(asOf)`. Изменения после `asOf` появятся после перезагрузки первой страницы. Список не вызывает `expire()` и не использует `operationId`.

### INV-03 — входящие приглашения пользователя

Вход: доверенный `userId` вошедшего пользователя, фильтр, курсор и limit по тем же правилам. Наличие роли `STUDENT` и `StudentProfile` **не требуется**: приглашённый пользователь может сначала увидеть приглашение, а затем пройти student-onboarding.

1. Выбрать только приглашения с `studentUserId == userId` и `attachedAt <= asOf`; email из запроса не используется для поиска. Привязка после начала просмотра не меняет уже открытую страницу.
2. Применить фильтр по эффективному статусу на зафиксированный `asOf`, сортировку `createdAt DESC, invitationId DESC` и cursor pagination.
3. Пакетно получить `TeacherProfileSummary` отправителей без `N+1`.
4. Вернуть приглашения, имена преподавателей, статусы и даты. Не изменять статус, не привязывать приглашения во время чтения, не создавать роль или профиль.

Непривязанное приглашение появится в списке только после утверждённой обработки подтверждённого account email. Отсутствие `TeacherProfileSummary` для существующего приглашения — нарушение целостности, а не основание раскрывать данные из Identity.

### INV-04 — принятие приглашения

Вход: `operationId`, `invitationId` и доверенный `studentUserId` вошедшего пользователя. Email из клиента не передаётся. Создание связи принадлежит этому сценарию invitation; отдельная команда relationship для её создания не открывается.

```text
BEGIN
1. Проверить или зарезервировать operationId; точный replay сразу вернуть.
2. Загрузить StudentInvitation с блокировкой.
3. Проверить, что studentUserId приглашения задан и совпадает с actor;
   чужое приглашение не раскрывать.
4. Проверить PENDING и now < expiresAt.
5. Проверить наличие StudentProfile адресата.
6. Проверить отсутствие TeacherStudent для пары из приглашения.
7. Вызвать StudentInvitation.accept(studentUserId, now).
8. Создать TeacherStudent(teacherUserId, studentUserId, now).
9. Сохранить приглашение, связь и результат идемпотентной операции.
COMMIT
```

**TeacherProfile отправителя повторно не проверяется.** Он был обязателен при INV-01; удаление профиля между INV-01 и INV-04 в v1 не предусмотрено. Роли также не проверяются Tutoring повторно: ими владеет Identity. `StudentProfile` необходим именно сейчас, поскольку известный адресат мог получить приглашение до student-onboarding. Если профиля ещё нет, приглашение остаётся `PENDING` и его можно принять после onboarding, пока срок не истёк.

При `now >= expiresAt` принять нельзя, даже если сохранённый статус ещё `PENDING`. Принятое приглашение без связи недопустимо: ошибка сохранения любой записи откатывает обе. Составной ключ `(teacherUserId, studentUserId)` защищает от дубля при гонке. Точный повтор с тем же `operationId` возвращает прежний результат; новая операция над терминальным приглашением отклоняется. Результат содержит `invitationId`, пару пользователей и момент создания связи, но не email или профильные данные.

### INV-05 — отклонение приглашения

Вход: `operationId`, `invitationId` и доверенный `userId` вошедшего адресата. Для отказа не требуются роль `STUDENT` и `StudentProfile`: пользователь может отказаться до student-onboarding. `TeacherProfile` отправителя повторно не проверяется.

```text
BEGIN
1. Проверить или зарезервировать operationId; точный replay сразу вернуть.
2. Загрузить StudentInvitation с блокировкой.
3. Проверить, что studentUserId приглашения задан и совпадает с actor;
   чужое приглашение не раскрывать.
4. Проверить PENDING и now < expiresAt.
5. Вызвать StudentInvitation.reject(actorUserId, now).
6. Сохранить приглашение с REJECTED и respondedAt = now,
   а также результат идемпотентной операции.
COMMIT
```

Отклонение не создаёт и не удаляет `TeacherStudent`, не меняет профили и не вызывает Identity. Конкурентные accept/reject сериализуются блокировкой приглашения; первая завершившаяся операция определяет терминальное состояние. Точный повтор того же `operationId` возвращает прежний результат; новая операция на терминальном или истёкшем приглашении отклоняется. При `now >= expiresAt` отказ недопустим даже при сохранённом `PENDING`.

### INV-06 — отдельный фоновый сценарий не создаётся в v1

Срок проверяется непосредственно в INV-01, INV-02/03, INV-04/05 и при привязке приглашения после подтверждения account email. Для корректности не требуется ежедневная материализация всех истёкших приглашений. Доменная операция `StudentInvitation.expire(now)` остаётся: INV-01 вызывает её для старого истёкшего `PENDING` на тот же адрес перед созданием нового приглашения. В прочих строках сохранённый `PENDING` может оставаться после срока, но query возвращает эффективный `EXPIRED`, а ответ на приглашение запрещён. Отдельная задача для очистки исторических записей может быть спроектирована позднее в Infrastructure при наличии политики хранения.

### INV-07 — привязка после подтверждения account email

`HandleAccountEmailVerifiedUseCase` после дедупликации события берёт общую с INV-01 блокировку нормализованного email. Он повторно проверяет через публичный Identity API, что событие относится к **текущему подтверждённому** account email указанного userId; устаревшее событие не применяется. В той же write-транзакции внутренний `AttachPendingInvitationsService` использует пакетный repository-метод наподобие `findActivePendingByEmailForUpdate(normalizedEmail, now)` и `updateAll(changedInvitations)`.

Для приглашения без `studentUserId` вызывается `attachStudent(userId, now)`, который одновременно устанавливает `attachedAt = now`; повтор для того же пользователя — no-op без изменения attachedAt; переназначение другому запрещено. Если после смены account email приглашение стало самоприглашением, его пропускают и оставляют `PENDING` до истечения. Истёкшие и терминальные приглашения не изменяются. Привязка не означает принятия и не создаёт роль, профиль или `TeacherStudent`. Receipt `eventId` сохраняется атомарно с изменениями приглашений и профилей.

### INV-08 — заявка на письмо

`InvitationNotificationSender.enqueue(StudentInvitationNotification)` — output port приложения. Неизменяемый payload содержит `invitationId`, зафиксированный `studentEmail`, `teacherDisplayName`, `expiresAt` и deduplication key `INVITATION_CREATED:{invitationId}`. Он не содержит `studentUserId`, роли либо признака существования аккаунта адресата. INV-01 ставит заявку в Notifications в своей транзакции вместе с приглашением и результатом идемпотентности; ошибка enqueue до commit откатывает все записи.

Notifications отправляет письмо после commit с retry при SMTP-сбое, но не выполняет первую отправку после `expiresAt`. Открытие ссылки не принимает приглашение. Привязка, принятие и отказ не создают дополнительных писем. Для реализации понадобится расширение публичного Notifications API/шаблона, поскольку текущий verification-only контракт не покрывает приглашения.

## 6. Relationship

Актуальное решение: `birthDate` хранится только в Identity; Tutoring показывает вычисленный возраст в self/public-view. Публичный активный профиль доступен без проверки TeacherStudent.

### Статус и назначение

Документ фиксирует согласованный проект блока `tutoring.application.relationship` и связанного с удалением связи межмодульного workflow.

Спецификация описывает четыре сценария:

1. Создание `TeacherStudent` после принятия приглашения.
2. Получение преподавателем списка связанных учеников.
3. Получение учеником списка связанных преподавателей.
4. Удаление связи преподаватель–ученик после блокировки создания и обработки будущих уроков.

Блок также предоставляет одиночные и пакетные проверки существования связи.

### Границы ответственности

`TeacherStudent` — самостоятельный Aggregate Root Tutoring со следующим минимальным состоянием:

```text
teacherUserId: UUID
studentUserId: UUID
createdAt: Instant
```

Идентичность задаётся парой:

```text
teacherUserId + studentUserId
```

Отдельный surrogate ID не используется. В PostgreSQL пара защищается составным первичным ключом:

```sql
PRIMARY KEY (teacher_user_id, student_user_id)
```

Основные правила:

- оба идентификатора обязательны;
- преподаватель и ученик не могут быть одним пользователем;
- одна пара может иметь только одну связь;
- связь не хранит данные профилей, ролей, приглашений, дат рождения и уроков;
- удаление связи не удаляет профили, роли, приглашения, завершённые уроки и другие связи пользователей;
- создание связи выполняется только при принятии приглашения;
- пользователь не может напрямую создать `TeacherStudent`.

`relationship` не должен:

- отправлять приглашения или письма;
- самостоятельно принимать или отклонять приглашения;
- создавать роли или профили;
- повторно проверять роли через Identity;
- проверять предмет урока;
- хранить профильные данные внутри `TeacherStudent`;
- обращаться к таблицам Identity или Scheduling;
- самостоятельно решать, как обрабатывать уроки при удалении связи.

### Итоговая структура блока

```text
tutoring.application.relationship
├── port
│   ├── in
│   │   ├── ListTeacherStudentsUseCase
│   │   └── ListStudentTeachersUseCase
│   └── out
│       ├── TeacherStudentRepository
│       └── RelationshipCursorCodec
├── model
│   ├── query
│   │   ├── ListTeacherStudentsQuery
│   │   ├── ListStudentTeachersQuery
│   │   └── RelationshipPageCursor
│   └── result
│       ├── StudentRelationshipResult
│       ├── StudentRelationshipPageResult
│       ├── TeacherRelationshipResult
│       └── TeacherRelationshipPageResult
├── service
│   ├── ListTeacherStudentsService
│   ├── ListStudentTeachersService
│   ├── TutoringRelationshipQueryService
│   └── TutoringRelationshipCommandService
├── mapper
│   └── RelationshipResultMapper
└── exception
    ├── InvalidRelationshipCursorException
    ├── TeacherStudentAlreadyLinkedException
    ├── TeacherStudentNotLinkedException
    └── RelationshipWriteConflictException
```

Отдельный пользовательский `CreateTeacherStudentUseCase` не создаётся. Удаление выполняется через доверенный внешний workflow, поэтому frontend также не получает прямой доступ к `TutoringRelationshipCommands`.

Публичные межмодульные интерфейсы `TutoringRelationshipQuery` и `TutoringRelationshipCommands` уже являются входными контрактами. Дублирующие application-порты для них не создаются.

### Сценарий 1 — создание связи после принятия приглашения

#### Владелец сценария

Пользователь принимает приглашение через сценарий блока `invitation`:

```text
AcceptStudentInvitationService
→ StudentInvitation.accept(...)
→ TeacherStudent.create(...)
→ сохранить приглашение
→ сохранить связь
→ общий commit
```

Создание связи является внутренним шагом принятия приглашения.

#### Предварительные условия Application

Application проверяет:

- идемпотентность `operationId`;
- существование приглашения;
- наличие `StudentProfile` ученика;
- отсутствие уже подтверждённой связи этой пары.

`TeacherProfile` отправителя уже проверен при создании приглашения (INV-01). Удаление профиля преподавателя между созданием и принятием в v1 не предусмотрено, поэтому повторная проверка при принятии не выполняется.

Tutoring не выполняет повторную проверку ролей `TEACHER` и `STUDENT`. Identity отвечает за роли, а утверждённый onboarding-workflow создаёт роль и соответствующий профиль атомарно.

`StudentInvitation.accept(...)` проверяет:

- статус `PENDING`;
- наличие прикреплённого ученика;
- совпадение адресата с аутентифицированным учеником;
- срок действия приглашения.

При `acceptedAt >= expiresAt` приглашение уже истекло.

`TeacherStudent.create(...)` проверяет обязательность идентификаторов и запрет связи пользователя с самим собой.

#### Транзакционная последовательность

```text
BEGIN

1. Проверить или зарезервировать operationId.
2. Загрузить StudentInvitation с блокировкой.
3. Проверить адресата, статус и срок приглашения.
4. Получить teacherUserId и studentUserId из приглашения.
5. Проверить наличие StudentProfile; TeacherProfile отправителя повторно не загружать.
6. Проверить отсутствие TeacherStudent.
7. Выполнить StudentInvitation.accept(studentUserId, acceptedAt).
8. Создать TeacherStudent(teacherUserId, studentUserId, acceptedAt).
9. Сохранить StudentInvitation.
10. Сохранить TeacherStudent.
11. Сохранить результат идемпотентной операции.

COMMIT
```

Принятое приглашение без созданного `TeacherStudent` недопустимо. Ошибка сохранения любого агрегата откатывает всю операцию.

Составной первичный ключ является последней защитой от конкурентного создания дубликата.

### Сценарий 2 — список учеников преподавателя

#### Входная модель

```java
public interface ListTeacherStudentsUseCase {
    StudentRelationshipPageResult execute(
        ListTeacherStudentsQuery query
    );
}
```

```java
public record ListTeacherStudentsQuery(
    UUID actorTeacherUserId,
    String cursor,
    int limit
) {}
```

`actorTeacherUserId` поступает из доверенного principal. Пользователь не передаёт идентификатор произвольного преподавателя.

Tutoring требует наличие собственного `TeacherProfile`, но не обращается в Identity для повторной проверки роли или активности аккаунта. Допуск пользователя к запросу обеспечивает authentication/authorization boundary.

#### Последовательность

```text
1. Получить actorTeacherUserId из доверенного контекста.
2. Потребовать наличие TeacherProfile.
3. Проверить limit и декодировать cursor.
4. Загрузить limit + 1 отношений через TeacherStudentRepository.
5. Если отношений нет — вернуть пустую страницу.
6. Собрать Set<studentUserId> текущей страницы.
7. Одним пакетным вызовом получить PublicStudentProfileView для участников связей.
8. Взять возраст из каждого PublicStudentProfileView; профильный сервис получает его пакетно из Identity.
9. Сформировать StudentRelationshipResult.
10. Сформировать непрозрачный nextCursor, если получен лишний элемент.
```

Множество `studentUserIds` используется для пакетной загрузки всей страницы и предотвращения `N+1`. Оно не означает поиск одной связи.

Внутренний сервис блока `profile` предоставляет операцию наподобие:

```java
Map<UUID, PublicStudentProfileView> findLinkedStudentProfileViews(
    Set<UUID> studentUserIds
);
```

`teacherUserId` этому методу не требуется: блок `relationship` уже получил все ID из подтверждённых связей текущего преподавателя.

`PublicStudentProfileView` формируется блоком `profile` и содержит локальные публичные данные, возраст из Identity и указанный контактный email. Метод вызывается только для ID, уже полученных из связей текущего преподавателя. Полный self-view `StudentProfileView` здесь не используется.

#### Результат

```java
public record StudentRelationshipResult(
    UUID studentUserId,
    Instant linkedAt,
    String displayName,
    String contactEmail,
    Set<String> subjectCodes,
    String photoUrl,
    int age
) {}
```

Nullable-поля в Java-аннотациях должны быть отмечены явно. `contactEmail` — указанный пользователем адрес без подтверждения. Возраст обязателен в public-view и вычисляется Identity по пакетному запросу; здесь список ограничен связями из-за назначения самого списка.

```java
public record StudentRelationshipPageResult(
    List<StudentRelationshipResult> items,
    String nextCursor
) {}
```

Пустой результат содержит `List.of()` и `nextCursor = null`.

### Сценарий 3 — список преподавателей ученика

#### Входная модель

```java
public interface ListStudentTeachersUseCase {
    TeacherRelationshipPageResult execute(
        ListStudentTeachersQuery query
    );
}
```

```java
public record ListStudentTeachersQuery(
    UUID actorStudentUserId,
    String cursor,
    int limit
) {}
```

Для v1 это только сценарий «мои преподаватели». Tutoring требует наличие собственного `StudentProfile`, но не проверяет роль через Identity.

#### Последовательность

```text
1. Получить actorStudentUserId из доверенного контекста.
2. Потребовать наличие StudentProfile.
3. Проверить limit и декодировать cursor.
4. Загрузить limit + 1 отношений.
5. Если отношений нет — вернуть пустую страницу.
6. Собрать Set<teacherUserId> текущей страницы.
7. Одним пакетным вызовом получить PublicTeacherProfileView для участников связей.
8. Взять возраст из каждого PublicTeacherProfileView; профильный сервис получает его пакетно из Identity.
9. Сформировать TeacherRelationshipResult.
10. Сформировать nextCursor при наличии следующей страницы.
```

Внутренний сервис блока `profile` предоставляет пакетную операцию:

```java
Map<UUID, PublicTeacherProfileView> findLinkedTeacherProfileViews(
    Set<UUID> teacherUserIds
);
```

Правила раскрытия профильных данных принадлежат блоку `profile`.

#### Результат

```java
public record TeacherRelationshipResult(
    UUID teacherUserId,
    Instant linkedAt,
    int age,
    String displayName,
    String contactEmail,
    Set<String> subjectCodes,
    String description,
    String photoUrl
) {}
```

```java
public record TeacherRelationshipPageResult(
    List<TeacherRelationshipResult> items,
    String nextCursor
) {}
```

Возраст возвращается в том числе связанному ученику, но доступен и в публичном профиле без связи. Контактный email указан владельцем профиля и не считается подтверждённым. Результат не содержит дату рождения, account email, роли, статус аккаунта, domain aggregate или статистику уроков.

### Cursor pagination

Стабильная сортировка основана только на неизменяемых данных связи:

```text
createdAt DESC
otherUserId DESC
```

```java
public record RelationshipPageCursor(
    Instant relationshipCreatedAt,
    UUID otherUserId
) {}
```

Параметры:

```text
limit по умолчанию: 50
допустимый диапазон: 1..100
fetchLimit: limit + 1
```

Курсор непрозрачен для клиента. `RelationshipCursorCodec` связывает его с владельцем и направлением списка. Курсор списка учеников нельзя использовать для списка преподавателей или для другого пользователя.

```java
public interface RelationshipCursorCodec {

    RelationshipPageCursor decodeTeacherStudents(
        String cursor,
        UUID teacherUserId
    );

    String encodeTeacherStudents(
        RelationshipPageCursor cursor,
        UUID teacherUserId
    );

    RelationshipPageCursor decodeStudentTeachers(
        String cursor,
        UUID studentUserId
    );

    String encodeStudentTeachers(
        RelationshipPageCursor cursor,
        UUID studentUserId
    );
}
```

### Repository-порт связи

```java
public interface TeacherStudentRepository {

    Optional<TeacherStudent> find(
        UUID teacherUserId,
        UUID studentUserId
    );

    Optional<TeacherStudent> findForUpdate(
        UUID teacherUserId,
        UUID studentUserId
    );

    boolean exists(
        UUID teacherUserId,
        UUID studentUserId
    );

    Set<UUID> findLinkedStudentUserIds(
        UUID teacherUserId,
        Set<UUID> studentUserIds
    );

    List<TeacherStudent> findByTeacher(
        UUID teacherUserId,
        RelationshipPageCursor after,
        int fetchLimit
    );

    List<TeacherStudent> findByStudent(
        UUID studentUserId,
        RelationshipPageCursor after,
        int fetchLimit
    );

    void insert(TeacherStudent relationship);

    boolean delete(
        UUID teacherUserId,
        UUID studentUserId
    );
}
```

Правила:

- `findByTeacher` и `findByStudent` реализуют keyset pagination в БД;
- `findLinkedStudentUserIds` выполняет пакетную проверку одним запросом;
- пустой внутренний набор возвращает пустой результат без запроса к БД;
- `insert` не заменяет существующую пару;
- `findForUpdate` используется write-сценарием удаления для защиты строки связи от конкурентного удаления;
- `delete == false` после успешной блокировки считается write-conflict.

Scheduling не использует `findForUpdate` Tutoring для блокировки создания уроков. Для этого применяется собственный согласованный guard Scheduling, описанный ниже.

### Одиночные и пакетные проверки связи

`TutoringRelationshipQueryService` реализует:

```java
boolean areLinked(
    UUID teacherUserId,
    UUID studentUserId
);

void requireLinked(
    UUID teacherUserId,
    UUID studentUserId
);

void requireAllLinked(
    UUID teacherUserId,
    Set<UUID> studentUserIds
);
```

`areLinked` выполняет один `repository.exists` и возвращает `false`, если связи нет.

`requireLinked` выбрасывает `TeacherStudentNotLinkedException`, если связи нет.

`requireAllLinked`:

1. Проверяет корректность входного набора.
2. Одним запросом получает связанные ID.
3. Вычисляет `requestedStudentIds - linkedStudentIds`.
4. При непустой разности возвращает ошибку со стабильно отсортированным полным набором отсутствующих ID.

Проверки связи не проверяют предмет, расписание, статус урока, роль или активность аккаунта.

### Сценарий 4 — удаление связи

#### Два уровня API

Пользовательская операция принадлежит внешнему workflow:

```text
TeacherStudentRemovalWorkflow
```

Workflow отвечает за:

- получение `requestingUserId` из доверенного контекста;
- проверку, что пользователь является преподавателем или учеником удаляемой пары;
- идемпотентность всей пользовательской операции;
- вызов Scheduling;
- вызов внутреннего межмодульного API Tutoring;
- формирование итогового результата, включая количество обработанных уроков и запросов.

Внутренний межмодульный API:

```java
public interface TutoringRelationshipCommands {
    TeacherStudentRemovalResult removeTeacherStudent(
        RemoveTeacherStudentCommand command
    );
}
```

Его вызывает только доверенный workflow. Frontend и HTTP-контроллер не могут вызывать этот контракт напрямую.

`TutoringRelationshipCommandService`:

- работает только внутри уже открытой внешней транзакции (`Propagation.MANDATORY`);
- не вызывает Scheduling;
- не проверяет таблицы уроков;
- блокирует и удаляет только `TeacherStudent`;
- сохраняет или проверяет согласованный результат идемпотентной межмодульной команды.

#### Scheduling guard

Перед обработкой уроков Scheduling получает транзакционную блокировку для конкретной пары:

```text
teacherUserId + studentUserId
```

Преподаватель целиком не блокируется. Удаление связи с одним учеником не должно препятствовать созданию уроков с другими учениками.

Один и тот же guard обязателен для двух сценариев:

```text
удаление связи
создание урока
```

Guard удерживается до завершения общей транзакции.

#### Последовательность удаления

```text
BEGIN

1. Workflow проверяет operationId и fingerprint.
2. При успешном повторе возвращает сохранённый результат до повторного вызова Scheduling.
3. Проверяет полномочия requestingUserId.
4. Scheduling получает guard для пары teacherUserId + studentUserId.
5. После получения guard workflow повторно требует существование TeacherStudent.
6. Scheduling отменяет будущие уроки этой пары.
7. Scheduling отклоняет незавершённые запросы этой пары.
8. Scheduling сохраняет необходимые outbox-сообщения.
9. Workflow вызывает TutoringRelationshipCommands.removeTeacherStudent(...).
10. Tutoring загружает TeacherStudent через findForUpdate.
11. Tutoring удаляет только TeacherStudent.
12. Workflow сохраняет итоговый результат operationId.

COMMIT
```

Если любой шаг завершается ошибкой, откатываются изменения Scheduling, outbox-записи и удаление связи.

#### Создание урока

Каждый сценарий создания урока для пары выполняет:

```text
BEGIN

1. Scheduling получает тот же guard пары.
2. После получения guard вызывает TutoringRelationshipQuery.requireLinked(...).
3. Если связь существует — создаёт урок.
4. Если связи нет — завершает операцию ошибкой.

COMMIT
```

Если создание урока первым получило guard, удаление дождётся его и обработает созданный урок. Если первым guard получило удаление, создание дождётся commit, после чего не найдёт связь и не создаст урок.

#### Политика обработки уроков

Перед удалением связи Scheduling:

- сохраняет завершённые уроки без изменений;
- отменяет будущие уроки;
- отклоняет незавершённые запросы на урок;
- не удаляет исторические данные;
- записывает необходимые уведомления в собственный outbox.

Отправка уведомлений выполняется после commit. Прямой вызов SMTP или Notifications внутри транзакции запрещён.

#### Результат

```java
public record TeacherStudentRemovalResult(
    UUID operationId,
    UUID teacherUserId,
    UUID studentUserId
) {}
```

Счётчики отменённых уроков и отклонённых запросов относятся к результату общего `UnlinkStudentWorkflow`/Scheduling, а не к команде Tutoring, которая удаляет только связь.

Повтор с тем же `operationId` и эквивалентным payload возвращает сохранённый результат без повторной обработки уроков. Тот же `operationId` с другим payload или типом приводит к `IdempotencyConflictException`. Новый `operationId` после удаления получает `TeacherStudentNotLinkedException`.

### Профильные и персональные данные

Распределение ответственности:

```text
relationship → подтверждает существование связи
profile      → хранит учебные данные и формирует публичное представление
Identity     → хранит birthDate и пакетно вычисляет возраст
```

Профили загружаются пакетно; возраст для этих же ID запрашивается у Identity одним batch-вызовом. Точная дата рождения в Tutoring не передаётся.

Self и public-view содержат возраст; минимальный summary его не содержит. Linked-методы также возвращают возраст, а проверка `TeacherStudent` нужна из-за контекста отношений, не из-за приватности возраста.

В результаты и логи не попадают:

- точная дата рождения любого пользователя;
- account email;
- роли и статус аккаунта.

### Транзакции и конкурентность

- Принятие приглашения и создание связи выполняются атомарно.
- Уникальность пары защищается составным первичным ключом.
- Списки выполняются в read-only транзакции и не блокируют строки.
- Весь removal workflow v1 выполняется в одной PostgreSQL-транзакции.
- Scheduling guard закрывает гонку между созданием урока и удалением связи.
- `findForUpdate` закрывает конкуренцию двух команд удаления одной связи.
- Модули изменяют только собственные данные через собственные API и репозитории.
- При переходе к отдельным базам общий transaction boundary потребуется заменить saga/process manager; это не решение v1.

### Критерии завершённости

- Связь создаётся только при принятии приглашения.
- Принятие приглашения и создание связи атомарны.
- Конкурентные принятия не создают дубликаты.
- Tutoring не повторяет проверку ролей Identity.
- Списки используют cursor pagination и стабильную сортировку.
- Профили и возраст из Identity загружаются пакетно без `N+1`.
- Возраст возвращается в публичном профиле активного пользователя без связи; дата рождения остаётся только в Identity.
- Указанный владельцем контактный email публичен без подтверждения владения.
- Пустой список возвращается как `List.of()`, а не `null`.
- Обычная проверка связи не проверяет предмет, расписание, роль или активность аккаунта.
- Пользовательское и внутреннее удаление разделены.
- Frontend не может удалить связь в обход workflow.
- Scheduling получает guard конкретной пары до обработки уроков.
- Создание урока использует тот же guard.
- Scheduling обрабатывает уроки до удаления `TeacherStudent`.
- Ошибка любого шага откатывает изменения уроков, outbox и удаление связи.
- Удаление не затрагивает профили, роли, приглашения, историю уроков и другие связи.
