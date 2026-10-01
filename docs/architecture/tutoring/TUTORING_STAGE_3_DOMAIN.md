# Tutoring — этап 3. Доменная модель

Статус: утверждённая спецификация этапа 3
Дата фиксации и утверждения пользователем: 2026-09-16
Актуальное решение: `birthDate` сохраняется в каждом учебном профиле и публична для активного профиля; настраиваемой видимости нет. Подтверждённая связь нужна для операций с отношением и уроками.
Синхронизация от 2026-09-24: у StudentInvitation добавлен `attachedAt` — время первой привязки к аккаунту.

## 1. Назначение и принятые решения

Документ фиксирует внутреннюю доменную модель `tutoring.domain`: учебные профили, справочник предметов, подтверждение профильной почты, приглашения и связь преподаватель–ученик. Для каждой области определены элементы, атрибуты, операции, инварианты, ошибки и границы ответственности application.

Исходные документы: `tutoring-stage-1-boundaries-and-use-cases.md` и `TUTORING_STAGE_2_PUBLIC_API.md` из той же папки. При противоречии последующие прямые решения пользователя имеют приоритет.

На этапе 3 утверждены следующие уточнения:

- Предметы обязательны для преподавателя, но необязательны для ученика.
- Domain разделён по предметным областям: `profile`, `subject`, `invitation`, `relationship`.
- В каждой области один пакет `model` объединяет агрегаты, сущности, value objects и enum. Отдельных пакетов `value` нет.
- Подтверждение профильной почты выделено в `profile.verification` с отдельным агрегатом и собственным `model`.
- В обоих профилях существует явная операция `changeContactEmail`.
- Передача текущего email не отменяет существующий pending email.
- `birthDate` является обязательным атрибутом обоих профилей и передаётся из Identity при их создании.
- Настраиваемого `BirthDateVisibility` нет; точная дата рождения и обычные профильные данные активного пользователя доступны публично.

Public Java API остаётся в `tutoring.api`. Входные `TeacherProfileData` и `StudentProfileData` остаются в `tutoring.api.model.profile.input`; HTTP DTO и jOOQ records не становятся доменными моделями.

Этот документ фиксирует архитектуру. Его утверждение не означает, что Java-код реализован или тесты выполнены. изменение кода и OpenAPI выполняется по отдельному запросу. Необходимые уточнения предыдущих документов зафиксированы при синхронизации этапа 5.

## 2. Общая структура Domain

### 2.1 Итоговое дерево пакетов

```text
tutoring.domain
├── profile
│   ├── model
│   │   ├── TeacherProfile
│   │   ├── StudentProfile
│   │   ├── ProfileEmail
│   │   ├── SubjectSelection
│   │   └── ProfileType
│   ├── exception
│   │   ├── EmptySubjectSelectionException
│   │   ├── InvalidExperienceException
│   │   ├── InvalidProfileNameException
│   │   ├── InvalidProfileEmailException
│   │   └── InvalidProfileEmailStateException
│   └── verification
│       ├── model
│       │   ├── ProfileEmailVerification
│       │   ├── VerificationTokenHash
│       │   └── ProfileEmailVerificationPurpose
│       └── exception
│           └── InvalidProfileEmailVerificationException
├── subject
│   ├── model
│   │   ├── Subject
│   │   └── SubjectCode
│   └── exception
│       ├── InvalidSubjectCodeException
│       └── InvalidSubjectNameException
├── invitation
│   ├── model
│   │   ├── StudentInvitation
│   │   ├── InvitationEmail
│   │   └── InvitationStatus
│   └── exception
│       ├── InvalidInvitationEmailException
│       ├── InvalidInvitationStateException
│       ├── InvitationExpiredException
│       ├── InvitationOwnershipException
│       └── SelfInvitationException
└── relationship
    ├── model
    │   └── TeacherStudent
    └── exception
        └── SelfRelationshipException
```

`model` — пакет, а не класс-контейнер. Он содержит полноценные доменные модели, включая операции и инварианты, а не только пассивные структуры данных. Различия между агрегатами, сущностями, value objects и enum обозначаются поведением типов .

Не создаются общий родитель `Profile`, универсальный `BaseAggregate`, общий склад `domain.model`, пустые пакеты событий и отдельные surrogate ID профилей.

### 2.2 Правила зависимостей

Domain использует собственные типы и стандартные Java-типы: `UUID`, `String`, `Instant`, `URI`, `List`, `Set`.

Запрещены зависимости Domain от Spring, Spring Security, HTTP DTO, `tutoring.api`, application, infrastructure, repositories, jOOQ, SMTP и API других бизнес-модулей.

Application обращается к Domain, но обратная зависимость запрещена. Существование аккаунта, роль, уникальность профиля, наличие кодов в справочнике и совместные транзакции обеспечиваются вне отдельного агрегата.

### 2.3 Общие соглашения

- `?` обозначает необязательный атрибут.
- Обязательные значения не могут быть `null`.
- Коллекции не содержат `null` и не выдаются наружу в изменяемом виде.
- Идентификаторы агрегатов неизменяемы.
- До изменения состояния проверяются все входные данные соответствующей операции.
- Публичных setters для произвольного изменения полей нет.
- Время передаётся явно; Domain не вызывает `Instant.now()`.
- Domain exceptions не содержат HTTP status, `ApiError` или локализованные пользовательские сообщения.

Нарушение Java-предусловий, например `null` вместо обязательного аргумента, — ошибка вызова. Нарушения содержательных правил выражаются доменными исключениями.

Приведённые ниже операции определяют архитектурный контракт; это не готовые Java-файлы. Конструкторы восстановления, getters и private helpers не перечисляются.

### 2.4 Package Specification → Description

| Package | Description |
|---|---|
| `tutoring.domain` | Определяет внутреннюю предметную модель учебных профилей, предметов, подтверждений профильной почты, приглашений и связей преподаватель–ученик. Защищает состояние отдельных агрегатов и допустимые операции. Не выполняет внешние запросы, HTTP-авторизацию, сохранение данных, доставку уведомлений или координацию межмодульных транзакций. |

## 3. Учебные профили

### 3.1 Назначение и границы агрегатов

`TeacherProfile` и `StudentProfile` — самостоятельные Aggregate Root. Каждый идентифицируется по `userId`, хранит собственные учебные и контактные данные и изменяется независимо от другого профиля того же аккаунта.

Одинаковый `userId` не означает общее изменяемое состояние: изменение email преподавателя не меняет email ученика.

Исключение — `birthDate`: оба профиля хранят локальную копию единой даты из `Identity.User`. Согласованность копий и источника обеспечивает application workflow, а не отдельный агрегат.

Профили не содержат связанные аккаунты, списки преподавателей или учеников, приглашения, verification-агрегаты или уроки.

### 3.2 TeacherProfile

Package: `tutoring.domain.profile.model`.

```text
TeacherProfile
├── userId: UUID
├── birthDate: LocalDate
├── displayName: String
├── contactEmail: ProfileEmail
├── pendingContactEmail: ProfileEmail?
├── contactEmailVerifiedAt: Instant?
├── contactDetails: List<String>
├── subjectSelection: SubjectSelection
├── description: String?
├── education: String?
├── experienceYears: Integer?
├── city: String?
└── photoUrl: URI?
```

Внутреннее поле `subjectSelection` представляет согласованный набор `subjectCodes`. Название и формат публичного поля `subjectCodes` не меняются.

Инварианты:

1. `userId` задан и неизменяем.
2. `birthDate` задан и не изменяется обычными профильными операциями.
3. `displayName` не пуст и не состоит только из пробелов.
4. `contactEmail` содержит корректное нормализованное значение.
5. Выбран минимум один предмет.
6. `experienceYears`, если указан, не отрицателен.
7. Pending email, если существует, отличается от текущего после нормализации.
8. `contactEmailVerifiedAt` относится только к текущему адресу.
9. Профиль не хранит вычисленный возраст.

Максимальные длины строк не вводятся произвольно на данном этапе; их нужно согласовать при проектировании входных контрактов и ограничений хранения.

### 3.3 StudentProfile

Package: `tutoring.domain.profile.model`.

```text
StudentProfile
├── userId: UUID
├── birthDate: LocalDate
├── displayName: String
├── contactEmail: ProfileEmail
├── pendingContactEmail: ProfileEmail?
├── contactEmailVerifiedAt: Instant?
├── contactDetails: List<String>
├── subjectSelection: SubjectSelection
└── photoUrl: URI?
```

Для общих полей действуют те же правила, что у TeacherProfile, кроме обязательности предметов:

```text
TeacherProfile: subjectSelection.size >= 1
StudentProfile: subjectSelection.size >= 0
```

Отсутствие предметов ученика представляется пустым набором, а не `null`. При наличии остальных обязательных данных такой профиль корректно заполнен.

Оба профиля содержат `birthDate`, но не содержат вычисленный `age`, account email, пароль, роли, account status, JWT или refresh token.

### 3.4 Создание и восстановление

Фабрика `create(...)` каждого профиля принимает `userId`, обязательный `birthDate` из доверенного registration/onboarding workflow и соответствующие профильные данные, кроме управляемых полей жизненного цикла почты.

Первоначальное состояние:

```text
contactEmail           = введённый профильный адрес
pendingContactEmail    = null
contactEmailVerifiedAt = null
```

Если application располагает подтверждённым основанием через Identity API, он вызывает `verifyCurrentContactEmail` в той же транзакции.

Настройки видимости даты рождения нет. Application не передаёт в Domain флаг видимости; публичный query проверяет активность профиля через Identity и не выдаёт неподтверждённый профильный email.

Восстановление сохранённого агрегата — задача persistence mapping. Оно не должно запускать первоначальное подтверждение, сбрасывать pending или заменять сохранённое время подтверждения.

### 3.5 Операции обычных данных

Для TeacherProfile:

```java
void updateDetails(
    String displayName,
    List<String> contactDetails,
    String description,
    String education,
    Integer experienceYears,
    String city,
    URI photoUrl
);
```

Для StudentProfile:

```java
void updateDetails(
    String displayName,
    List<String> contactDetails,
    URI photoUrl
);
```

Необязательные аргументы соответствуют атрибутам с `?`. Переданное значение заменяет прежнее, включая очистку необязательного поля через `null`. Все данные проверяются до изменения полей.

`updateDetails` не изменяет `userId`, `birthDate`, выбор предметов, текущий/pending email и время подтверждения.

Общие операции:

```java
void changeSubjects(SubjectSelection selection);
```

`changeSubjects` заменяет выбор целиком. Преподаватель отклоняет пустой выбор; ученик может очистить выбор. Существование кодов уже проверено application.

### 3.6 Операции почты

Оба профиля имеют:

```java
boolean changeContactEmail(ProfileEmail newEmail);

void verifyCurrentContactEmail(
    ProfileEmail expectedEmail,
    Instant verifiedAt
);

void confirmPendingContactEmail(
    ProfileEmail expectedEmail,
    Instant verifiedAt
);
```

`changeContactEmail` возвращает `true` только при появлении другого pending адреса. Это позволяет application отличить реальную смену от повторного вызова без дополнительного result-класса.

Подробные правила определены в разделе 5.

### 3.7 Value objects и enum в profile.model

| Элемент | Атрибуты и ответственность |
| --- | --- |
| `ProfileEmail` | `value: String`. Неизменяемое нормализованное значение email. Проверяет формат и сравнивается по значению; не хранит подтверждение. |
| `SubjectSelection` | `codes: Set<SubjectCode>`. Неизменяемый выбор, допускающий пустой набор. Запрещает `null`; не проверяет справочник. |
| `ProfileType` | `TEACHER`, `STUDENT`. Различает тип профиля в verification; не является ролью аккаунта. |

У SubjectSelection достаточно операций `codes()` и `isEmpty()`. Изменение выбора создаёт новое значение, а не меняет его внутренний набор.

Нормализация ProfileEmail соответствует согласованному контракту Identity, но тип не импортирует `identity.domain.Email`. Подтверждение одного и того же адреса хранится отдельно в каждом профиле.

Точная дата рождения не имеет настраиваемой политики. Владелец получает её в self-view, любой посетитель — в публичном представлении активного профиля. Проверка `TeacherStudent` нужна для просмотра списка отношений или статистики уроков, а не для доступа к дате. Сам Domain профиля не выполняет эти проверки.

### 3.8 Граница ответственности application

| Правило или действие | Ответственный |
|---|---|
| Непустое имя, неотрицательный опыт | Профиль |
| Корректный нормализованный email | ProfileEmail |
| Состав и неизменяемость выбора | SubjectSelection |
| Минимум один предмет | Только TeacherProfile |
| Получение `birthDate` при создании профиля | Application из Identity через доверенный workflow |
| Согласованность даты в Identity и двух профилях | Application workflow |
| Раскрытие даты другому пользователю | Application только после проверки `TeacherStudent` |
| Существование пользователя и роли | Application через Identity API |
| Отсутствие другого профиля этого типа | Application и БД |
| Существование всех кодов | Application через справочник Tutoring |
| Ownership и активность пользователя | Application |
| Изменение профиля вместе с verification | Application-транзакция |

Предметы преподавателя описывают специализацию и не ограничивают предмет урока. Методы `canTeachSubject`, `teachesSubject` и подобные проверки разрешения не добавляются.

### 3.9 Package Specification → Description

| Package | Description |
|---|---|
| `tutoring.domain.profile` | Доменная область независимых учебных профилей и их контактной почты. Не управляет аккаунтами, отношениями между пользователями или уроками. |
| `tutoring.domain.profile.model` | Содержит агрегаты TeacherProfile и StudentProfile, профильные value objects и enum. Защищает внутренние правила данных и изменений профиля. Не содержит API/HTTP DTO и не обращается к repositories, Identity API или SecurityContext. |
| `tutoring.domain.profile.exception` | Нарушения внутренних правил профилей и их значений. Не выражает ошибки отсутствующего аккаунта, HTTP-авторизации или хранения данных. |

### 3.10 Ответственность типов

| Элемент | Description |
|---|---|
| `TeacherProfile` | Самостоятельный агрегат учебных и контактных данных преподавателя, идентифицируемый по userId. Хранит обязательную локальную копию birthDate, проверяет заполненность имени, обязательный выбор предметов, неотрицательный опыт и допустимые изменения профильной почты. Не наследует Identity.User, не хранит роли и не определяет право вести урок по выбранному предмету. |
| `StudentProfile` | Самостоятельный агрегат учебных и контактных данных ученика, идентифицируемый по userId. Хранит обязательную локальную копию birthDate, защищает заполненность профиля и изменения почты; допускает отсутствие выбранных предметов. Не хранит вычисленный возраст, преподавателей, приглашения или уроки. |
| `ProfileEmail` | Неизменяемое нормализованное значение контактного email профиля. Обеспечивает локальную корректность и сравнение адресов. Не содержит состояния подтверждения и не изменяет email аккаунта. |
| `SubjectSelection` | Неизменяемый выбор уникальных SubjectCode, допускающий пустой набор. Используется обоими профилями; обязательность выбора преподавателя проверяет TeacherProfile. Не проверяет существование кодов и не определяет право вести урок. |
| `ProfileType` | Внутренний тип учебного профиля для адресации verification. Не является UserRole Identity и не предоставляет права доступа. |

## 4. Предметы

### 4.1 Назначение и элементы

Область subject определяет справочник Tutoring и стабильный код предмета. Scheduling получает необходимые проверки через публичный API, не импортируя Domain.

| Элемент | Package |
| --- | --- |
| Subject | `tutoring.domain.subject.model` |
| SubjectCode | `tutoring.domain.subject.model` |

```text
Subject
├── code: SubjectCode
└── name: String

SubjectCode
└── value: String
```

### 4.2 Инварианты и операции

- Идентичность Subject задаётся кодом.
- Код существующего предмета не изменяется.
- Название обязательно, не пустое и не состоит только из пробелов.
- SubjectCode неизменяем, не пуст и соответствует согласованному формату справочника.
- Конструирование SubjectCode не создаёт предмет и не проверяет наличие строки в БД.
- В v1 нет пользовательских операций добавления, удаления или переименования справочника.

Достаточно создания корректной сущности и чтения атрибутов. Начальные данные и обслуживание справочника относятся к миграциям и административному процессу.

Синтаксически корректный код и существующий код — разные понятия. Наличие проверяет application.

### 4.3 Package Specification → Description

| Package | Description |
|---|---|
| `tutoring.domain.subject` | Определяет справочные предметы и стабильные коды. Используется профилями для выбора направлений обучения. Не проверяет специализацию преподавателя как разрешение проводить урок. |
| `tutoring.domain.subject.model` | Содержит справочную сущность Subject и value object SubjectCode. Не предоставляет пользовательский жизненный цикл управления справочником в v1 и не выполняет запросы к БД. |
| `tutoring.domain.subject.exception` | Нарушения локальной корректности кода и названия. Не содержит ошибок поиска в справочнике или HTTP-ответов. |

### 4.4 Ответственность типов

| Элемент | Description |
|---|---|
| Subject | Справочная сущность предмета со стабильным SubjectCode и отображаемым названием. Используется для проверки и отображения предметов профилей и уроков. Не определяет право преподавателя вести урок и не изменяется через пользовательские сценарии v1. |
| SubjectCode | Неизменяемое значение стабильного кода предмета. Обеспечивает локальную корректность и сравнение кодов; используется Subject и SubjectSelection. Не обращается к справочнику и не содержит данных преподавателя. |

## 5. Подтверждение профильной почты

### 5.1 Назначение и граница агрегата

ProfileEmailVerification — отдельный Aggregate Root. Профиль хранит адреса и подтверждение текущего адреса; verification хранит право выполнить конкретное одноразовое подтверждение.

Verification ссылается на профиль через `userId + profileType`, не содержит профиль в объектном графе и не изменяет его самостоятельно. Application координирует оба агрегата.

Verification преподавателя не может изменить профиль ученика того же пользователя.

### 5.2 Элементы и атрибуты

Все элементы находятся в `tutoring.domain.profile.verification.model`.

| Элемент |
| --- |
| ProfileEmailVerification |
| VerificationTokenHash |
| ProfileEmailVerificationPurpose |

```text
ProfileEmailVerification
├── id: UUID
├── userId: UUID
├── profileType: ProfileType
├── targetEmail: ProfileEmail
├── purpose: ProfileEmailVerificationPurpose
├── tokenHash: VerificationTokenHash
├── createdAt: Instant
├── expiresAt: Instant
├── consumedAt: Instant?
├── invalidatedAt: Instant?
└── expiredAt: Instant?

VerificationTokenHash
└── value: String

ProfileEmailVerificationPurpose
├── INITIAL_CONFIRMATION
└── EMAIL_CHANGE
```

INITIAL_CONFIRMATION подтверждает первоначальный текущий адрес. EMAIL_CHANGE подтверждает pending адрес и завершает замену.

Идентификатор, адресат, тип профиля, цель, purpose, хеш и даты создания/истечения неизменяемы.

VerificationTokenHash хранит только результат хеширования. Генерация raw token, алгоритм и поиск по предъявленному токену относятся к application/output ports и infrastructure. Тип принадлежит Tutoring; одноимённый тип Identity не импортируется.

### 5.3 Операции verification

```java
static ProfileEmailVerification create(
    UUID id,
    UUID userId,
    ProfileType profileType,
    ProfileEmail targetEmail,
    ProfileEmailVerificationPurpose purpose,
    VerificationTokenHash tokenHash,
    Instant createdAt,
    Instant expiresAt
);

boolean isActiveAt(Instant now);

void consume(Instant now);

void invalidate(Instant now);

boolean expire(Instant now);
```

При создании `consumedAt = null`, `invalidatedAt = null`, `expiredAt = null`, `expiresAt > createdAt`.

isActiveAt возвращает true только при:

```text
createdAt <= now < expiresAt
consumedAt == null
invalidatedAt == null
expiredAt == null
```

Метод read-only.

consume допустим только для активного verification, устанавливает consumedAt и запрещает повторное применение.

invalidate аннулирует ещё не использованный запрос. Повторное аннулирование безопасно. Использованный запрос не становится активным, а consumedAt не заменяется другим состоянием.

Истечение определяется временем: `isActiveAt(now)` возвращает `false` уже при `now >= expiresAt`, даже если ежедневная задача ещё не заполнила `expiredAt`. `expire(now)` материализует истечение только для неиспользованного и неаннулированного запроса при `now >= expiresAt`; повторный вызов безопасен и возвращает `false`. Отдельный изменяемый status не требуется. Ежедневная задача не меняет профильный current/pending email.

### 5.4 Первоначальное подтверждение

Application проверяет состояние account email через Identity API:

1. Если первоначальный адрес профиля совпадает с уже подтверждённым account email, вызывается `verifyCurrentContactEmail`.
2. Если он совпадает с ещё неподтверждённым account email, профиль ждёт `AccountEmailVerifiedEvent`. Отдельный `INITIAL_CONFIRMATION` verification и письмо Tutoring не создаются.
3. Если адрес профиля отличается от account email, application создаёт `INITIAL_CONFIRMATION` verification и надёжно инициирует письмо через Notifications.

Правило применяется к каждому созданному профилю. Когда оба профиля имеют тот же ещё неподтверждённый account email, одно подтверждение в Identity затем подтверждает оба current адреса.

Владелец видит свой неподтверждённый адрес. Другим пользователям он не раскрывается и для транзакционных учебных уведомлений не используется.

### 5.5 Поведение changeContactEmail

| Вход | Изменение профиля | Результат |
|---|---|---|
| Адрес равен текущему | Нет изменений; существующий pending сохраняется | false |
| Адрес равен существующему pending | Нет изменений | false |
| Адрес отличается от обоих | Заменяется pendingContactEmail | true |

Текущий адрес и его время подтверждения сохраняются.

При результате true application одной транзакцией:

1. Изменяет pending профиля.
2. Аннулирует прежние неиспользованные EMAIL_CHANGE verification этого профиля.
3. Подтверждает новый адрес через разрешённый Identity API либо создаёт новый verification.
4. Сохраняет изменения и необходимые delivery requests.

Сам changeContactEmail не аннулирует другой агрегат и не отправляет письмо.

Передача того же pending не продлевает токен и не переотправляет письмо. Переотправка — отдельный application-сценарий.

### 5.6 Подтверждение текущего адреса

verifyCurrentContactEmail требует совпадения expectedEmail с текущим адресом.

Если текущий адрес не подтверждён, устанавливается contactEmailVerifiedAt. Если уже подтверждён, повторная обработка не перезаписывает время. Pending не изменяется.

Основание проверяет application: активный verification либо разрешённый подтверждённый факт Identity. Поэтому текущий адрес A можно подтвердить, пока pending B ещё ожидает подтверждения.

### 5.7 Подтверждение pending

confirmPendingContactEmail:

1. Проверяет существование pending.
2. Проверяет совпадение с expectedEmail.
3. Переносит pending в contactEmail.
4. Очищает pending.
5. Устанавливает время подтверждения нового текущего адреса.

При отсутствующей или несовпадающей цели возникает InvalidProfileEmailStateException.

Application после замены аннулирует оставшиеся неиспользованные запросы, относящиеся к прежнему текущему адресу или завершённой смене.

### 5.8 Защита от устаревших подтверждений

```text
Текущий адрес A
pending B → запрос V1
pending C → V1 аннулирован, создан V2
pending B → V2 аннулирован, создан новый V3
```

Токен V1 не применяется, хотя адрес снова B. Application проверяет конкретный verification, а не только совпадение email:

- Запрос активен.
- Пользователь и тип профиля совпадают.
- Purpose соответствует операции.
- Цель актуальна для текущего состояния профиля.

Проверка, consumption и изменение профиля выполняются атомарно с защитой от конкурентной смены адреса.

В одном профиле могут одновременно ожидать подтверждения первоначальный текущий адрес и pending. У запросов разные purpose; они не должны случайно аннулировать друг друга.

### 5.9 Повторы и срок

Использованный токен не применяется повторно. Безопасный повтор уже обработанного события или внутренней операции обеспечивается application-идемпотентностью, а не повторным разрешением consume.

Domain принимает expiresAt и проверяет даты. Конкретная длительность, переотправка и rate limits определяются на этапе Application.

### 5.10 Package Specification → Description

| Package | Description |
|---|---|
| `tutoring.domain.profile.verification` | Доменная область одноразового подтверждения текущего и pending профильного email. Принадлежит Tutoring; не использует verification-агрегаты Identity как свои. |
| `tutoring.domain.profile.verification.model` | Содержит ProfileEmailVerification, VerificationTokenHash и enum назначения. Защищает жизненный цикл запроса, но не изменяет профиль, не генерирует raw token и не доставляет письма. |
| `tutoring.domain.profile.verification.exception` | Нарушения жизненного цикла verification. Не раскрывает raw token или tokenHash и не содержит транспортных ошибок. |

### 5.11 Ответственность типов

| Элемент | Description |
|---|---|
| ProfileEmailVerification | Самостоятельный агрегат одноразового подтверждения email конкретного профиля. Фиксирует пользователя, тип профиля, цель, назначение, хеш, срок и использование/аннулирование. Защищает от повторного применения и истечения. Ссылается на профиль через userId и ProfileType, не содержит его и не изменяет самостоятельно. |
| VerificationTokenHash | Неизменяемое значение хеша токена, используемое вместо raw token. Не генерирует, не хеширует и не доставляет токен. |
| ProfileEmailVerificationPurpose | Внутреннее назначение запроса: первоначальное подтверждение текущего адреса или подтверждение его смены. Определяет соответствующую операцию профиля и не относится к активации аккаунта Identity. |

## 6. Приглашения ученика

### 6.1 Назначение и элементы

StudentInvitation фиксирует предложение преподавателя установить связь с адресатом. Имеет собственную идентичность и терминальный жизненный цикл. Новое приглашение — новый агрегат, а не возврат прежнего в PENDING.

Все элементы находятся в `tutoring.domain.invitation.model`.

| Элемент |
| --- |
| StudentInvitation |
| InvitationEmail |
| InvitationStatus |

```text
StudentInvitation
├── id: UUID
├── teacherUserId: UUID
├── studentEmail: InvitationEmail
├── studentUserId: UUID?
├── attachedAt: Instant?
├── status: InvitationStatus
├── createdAt: Instant
├── expiresAt: Instant
└── respondedAt: Instant?

InvitationEmail
└── value: String

InvitationStatus
├── PENDING
├── ACCEPTED
├── REJECTED
└── EXPIRED
```

Неизменяемы id, teacherUserId, studentEmail, createdAt, expiresAt. studentUserId и attachedAt могут первоначально отсутствовать, затем заполняются вместе; переназначение другому пользователю и изменение времени первой привязки запрещены.

InvitationEmail — зафиксированная цель, а не текущая почта аккаунта или профиля. Нормализация соответствует контракту Identity, но типы не взаимозаменяемы:

```text
InvitationEmail ≠ ProfileEmail ≠ Identity Email
```

### 6.2 Создание и внешние предусловия

```java
static StudentInvitation create(
    UUID id,
    UUID teacherUserId,
    InvitationEmail studentEmail,
    UUID studentUserId,        // может отсутствовать
    Instant createdAt
);
```

Фабрика устанавливает PENDING, respondedAt = null и expiresAt = createdAt + 30 суток. Если studentUserId уже известен, attachedAt = createdAt; иначе attachedAt = null. Это длительность 30 × 24 часа, а не календарный месяц.

Известный studentUserId должен отличаться от teacherUserId.

Application до создания проверяет активность, роль и профиль преподавателя, запрет самоприглашения по разрешённым данным Identity, отсутствие действующего приглашения от этого преподавателя на тот же нормализованный email и отсутствие подтверждённой связи с найденным адресатом.

Незарегистрированного пользователя можно пригласить. Отсутствие роли STUDENT у найденного адресата не препятствует ожиданию: роль и профиль понадобятся до принятия.

### 6.3 Доменные операции

```java
boolean attachStudent(UUID studentUserId, Instant now);

void accept(UUID actorStudentUserId, Instant now);

void reject(UUID actorStudentUserId, Instant now);

boolean isExpiredAt(Instant now);

InvitationStatus effectiveStatusAt(Instant now);

boolean expire(Instant now);
```

### 6.4 Связывание с аккаунтом

attachStudent допустим только для неистёкшего PENDING:

- Адресат отсутствует — установить его и attachedAt = now, вернуть true.
- Уже назначен тот же — ничего не менять, включая attachedAt, вернуть false.
- Назначен другой — отклонить переназначение через InvitationOwnershipException.
- Адресат совпадает с преподавателем — SelfInvitationException.

Application вызывает операцию на основании подтверждённого account email. Операция не проверяет владение адресом, не добавляет роль, не создаёт профиль, не принимает приглашение и не создаёт TeacherStudent.

Терминальные и истёкшие приглашения обработчик не связывает.

### 6.5 Принятие и отклонение

Для обеих операций:

1. studentUserId задан и совпадает с actor.
2. Сохранённый статус PENDING.
3. now < expiresAt.
4. Ответ не предшествует созданию.

Ownership проверяется до раскрытия деталей состояния чужого приглашения.

accept устанавливает ACCEPTED и respondedAt = now. reject устанавливает REJECTED и respondedAt = now.

Повторный ответ на терминальное приглашение отклоняется доменной ошибкой состояния. Идемпотентность межмодульных команд из этапа 2 не распространяется автоматически на эти операции.

Активность пользователя проверяет Identity/auth-граница; наличие StudentProfile и отсутствие связи проверяет application. Tutoring не выполняет повторную проверку роли при ответе на приглашение.

### 6.6 Истечение и чтение

isExpiredAt проверяет now >= expiresAt. Сам результат не меняет терминальный статус ранее принятого или отклонённого приглашения.

effectiveStatusAt возвращает EXPIRED только для сохранённого PENDING с истёкшим сроком; в остальных случаях возвращает сохранённый статус. Метод ничего не записывает.

Метод не восстанавливает исторический статус cursor-страницы на прежний `asOf`, если приглашение было принято или отклонено позднее. Исторический статус вычисляет read-query Application/Persistence по `respondedAt` и `expiresAt` до применения фильтра страницы.

expire переводит истёкший PENDING в EXPIRED и возвращает true; в остальных случаях ничего не меняет и возвращает false. respondedAt не устанавливается, поскольку истечение не является ответом ученика.

Корректность принятия обеспечивается проверкой `now < expiresAt`, даже если сохранённый статус ещё PENDING. Отдельной плановой задачи материализации истёкших приглашений в v1 нет; `expire(now)` используется при создании нового приглашения на тот же адрес.

### 6.7 Переходы и инварианты времени

```text
PENDING → ACCEPTED
PENDING → REJECTED
PENDING → EXPIRED
```

Из терминальных состояний переходов нет.

studentUserId и attachedAt либо оба заданы, либо оба отсутствуют; при наличии createdAt <= attachedAt < expiresAt. Для ACCEPTED и REJECTED: studentUserId, attachedAt и respondedAt заданы, attachedAt <= respondedAt < expiresAt. Для PENDING и EXPIRED: respondedAt = null.

### 6.8 Атомарное принятие

Application одной транзакцией загружает приглашение с защитой от гонок, проверяет внешние условия, вызывает accept, создаёт TeacherStudent и сохраняет оба изменения. Ошибка создания связи откатывает принятие.

StudentInvitation не вызывает repository и не создаёт другой агрегат внутри accept.

После последующей отвязки историческое приглашение остаётся ACCEPTED: статус фиксирует состоявшееся принятие, а не вечное существование связи. Новое приглашение создаётся отдельным агрегатом при отсутствии действующего приглашения и подтверждённой связи.

### 6.9 Package Specification → Description

| Package | Description |
|---|---|
| `tutoring.domain.invitation` | Определяет приглашение ученика, его адресата и терминальный жизненный цикл. Не регистрирует пользователей и не создаёт учебные профили. |
| `tutoring.domain.invitation.model` | Содержит StudentInvitation, InvitationEmail и InvitationStatus. Защищает связывание, ответ и истечение. Не координирует сохранение связи и не доставляет письмо. |
| `tutoring.domain.invitation.exception` | Нарушения состояния, срока, адресата и запрета самоприглашения. Не содержит транспортных статусов и данных токенов. |

### 6.10 Ответственность типов

| Элемент | Description |
|---|---|
| StudentInvitation | Самостоятельный агрегат приглашения ученика преподавателем. Фиксирует email цели, допускает связывание с подтверждённым аккаунтом и защищает переходы из PENDING. Проверяет адресата и срок при ответе. Не создаёт роль, профиль, связь или письмо; принятие и создание связи координирует application. |
| InvitationEmail | Неизменяемое нормализованное значение первоначальной цели приглашения. Сохраняется независимо от изменений аккаунта или профиля. Не является контактным email и не подтверждает владение адресом. |
| InvitationStatus | Состояния жизненного цикла приглашения: PENDING, ACCEPTED, REJECTED, EXPIRED. Сам enum не выполняет переходы; правила защищает StudentInvitation. |

## 7. Связь преподаватель–ученик

### 7.1 Назначение и атрибуты

Package: `tutoring.domain.relationship.model`.

```text
TeacherStudent
├── teacherUserId: UUID
├── studentUserId: UUID
└── createdAt: Instant
```

Идентичность — направленная пара teacherUserId + studentUserId. Стороны имеют разные значения в сценарии. Отдельный surrogate UUID и value object составного ID в v1 не добавляются.

Все атрибуты после создания неизменяемы. Связь не входит в объектный граф профилей.

### 7.2 Создание и внешние условия

```java
static TeacherStudent create(
    UUID teacherUserId,
    UUID studentUserId,
    Instant createdAt
);
```

Внутренние правила: идентификаторы заданы и различаются, время задано.

Роль и TeacherProfile отправителя обеспечиваются при создании приглашения; повторная проверка TeacherProfile при принятии в v1 не выполняется, поскольку удаление профиля между этими действиями не предусмотрено. При принятии Application проверяет StudentProfile адресата, отсутствие дубля пары и атомарно сохраняет связь вместе с ACCEPTED. Роли принадлежат Identity и не проверяются повторно в Tutoring. createdAt берётся из момента принятия.

Фабрика не может самостоятельно доказать принятие другого агрегата: это правило совместного application-сценария.

### 7.3 Удаление

Нет операций изменения сторон и дополнительного статуса REMOVED.

Application удаляет связь через output port в утверждённом UnlinkStudentWorkflow после обработки будущих уроков, в общей транзакции.

Domain не реализует unlinkAndCancelLessons и не обращается к Scheduling.

Повтор с тем же operationId обслуживается идемпотентностью command API. Новая операция на отсутствующую пару получает публичную ошибку этапа 2. operationId и история команд не помещаются в TeacherStudent.

### 7.4 Package Specification → Description

| Package | Description |
|---|---|
| `tutoring.domain.relationship` | Определяет подтверждённую направленную связь преподавателя и ученика. Не управляет приглашениями, аккаунтами или уроками. |
| `tutoring.domain.relationship.model` | Содержит самостоятельный TeacherStudent с идентичностью пары пользователей. Не включает профили в объектный граф и не координирует удаление с Scheduling. |
| `tutoring.domain.relationship.exception` | Нарушения запрета связи пользователя с самим собой. Уникальность и отсутствие связи обрабатываются application/persistence. |

### 7.5 Ответственность типов

TeacherStudent — самостоятельный агрегат подтверждённой связи преподавателя и ученика, идентифицируемый направленной парой userId. Хранит момент создания и запрещает совпадение сторон. Создаётся application при принятии приглашения и удаляется после обработки уроков внешним workflow. Не содержит профили, приглашение или уроки в объектном графе.

## 8. Доменные ошибки

Все перечисленные исключения — внутренние unchecked exceptions.

| Исключение | Причина и машинно читаемый контекст |
|---|---|
| EmptySubjectSelectionException | Пустой выбор преподавателя. Не возникает из-за пустого выбора ученика. |
| InvalidExperienceException | Отрицательное experienceYears. |
| InvalidProfileNameException | Пустое или пробельное имя профиля. |
| InvalidProfileEmailException | Некорректный ProfileEmail; причина без обязательного сохранения исходного адреса. |
| InvalidProfileEmailStateException | Нет pending либо подтверждаемая цель не совпадает с соответствующим адресом профиля. |
| InvalidProfileEmailVerificationException | Нарушены даты или использован неактивный verification; verificationId и причина. |
| InvalidSubjectCodeException | Некорректное значение кода; не означает отсутствие в БД. |
| InvalidSubjectNameException | Пустое или пробельное название. |
| InvalidInvitationEmailException | Некорректная цель приглашения. |
| InvalidInvitationStateException | Операция запрещена для статуса; invitationId, текущий статус, операция. |
| InvitationExpiredException | Ответ или связывание после истечения; invitationId, expiresAt. |
| InvitationOwnershipException | Ответ не от адресата либо попытка переназначить его. |
| SelfInvitationException | Известный адресат совпадает с преподавателем. |
| SelfRelationshipException | Совпадают стороны связи. |

Описание каждого исключения фиксирует нарушение внутреннего доменного правила; исключение не содержит HTTP status, локализованное сообщение, raw token, tokenHash или зависимость от публичного API.

В Domain не добавляются UserNotFound, RoleRequired, ProfileAlreadyExists, UnknownSubjects, Unauthorized, Forbidden и IdempotencyConflict: это внешние условия, application или public API. Application преобразует ожидаемые доменные нарушения в ошибки своей границы; Domain не импортирует `tutoring.api.exception`.

## 9. Общая карта зависимостей и событий

### 9.1 Внутри Domain

```text
profile.model
  → subject.model                 # только SubjectCode
  → profile.exception

profile.verification.model
  → profile.model                 # ProfileEmail и ProfileType
  → profile.verification.exception

subject.model
  → subject.exception

invitation.model
  → invitation.exception

relationship.model
  → relationship.exception
```

Объединение типов в model не даёт права включать чужой агрегат в объектный граф. Использование SubjectCode не означает зависимость профиля от изменяемого Subject.

StudentInvitation и TeacherStudent не зависят друг от друга: совместный сценарий находится в application.

### 9.2 За пределами Domain

На package/component diagram:

```text
tutoring.application    → tutoring.domain
tutoring.application    → разрешённые публичные API
tutoring.infrastructure → application ports / domain
```

На class diagram Application будут показаны dependencies сервисов к нужным агрегатам.

Запрещено:

```text
domain       → application / infrastructure / presentation
domain       → tutoring.api
domain       → identity.* / scheduling.* / notifications.*
profile      → repository
invitation   → SMTP
relationship → Lesson
```

Внутренний `ProfileType` не наследуется от публичного `ProfileTypeView`. Преобразование выполняет application mapping; типы друг от друга не зависят. Типов `BirthDateVisibility` и `BirthDateVisibilityView` больше нет.

### 9.3 События

В v1 исходящие публичные и внутренние domain events не добавляются без конкретного обработчика.

Notifications вызывается через утверждённую application/infrastructure интеграцию. AccountEmailVerifiedEvent принадлежит Identity и не становится типом Domain Tutoring.

## 10. Проверки и критерии реализации

Это требования к будущим тестам. В рамках фиксации архитектуры Java-код не реализован и тесты не выполнялись.

### 10.1 Domain tests: профили и предметы

- Пустое имя и отрицательный опыт отклоняются.
- Пустой выбор преподавателя отклоняется; ученика принимается.
- Создание каждого профиля без `birthDate` отклоняется.
- Обычные операции профиля не изменяют `birthDate`.
- Изменение исходной коллекции не меняет сохранённое значение.
- Возвращённые коллекции не позволяют изменить агрегат.
- Обычное обновление не сбрасывает подтверждение email.
- Профили двух ролей изменяются независимо.
- Профиль и SubjectSelection не обращаются к справочнику.
- Синтаксически корректный SubjectCode не означает наличие предмета.
- Предметы преподавателя не порождают разрешение на проведение урока.

### 10.2 Domain tests: профильная почта

- Новый pending не заменяет текущий адрес до подтверждения.
- Передача текущего сохраняет существующий pending.
- Передача того же pending не меняет состояние.
- Подтверждение текущего не очищает pending.
- Несовпадающая pending цель отклоняется.
- Verification неактивен при now == expiresAt.
- Ежедневная материализация устанавливает `expiredAt` только после наступления срока и не влияет на синхронную проверку времени.
- Использованный или аннулированный запрос нельзя применить.
- Повторное аннулирование безопасно.
- Raw token отсутствует в состоянии verification.

### 10.3 Domain tests: приглашения и связь

- Приглашение создаётся в PENDING на 30 суток.
- Привязка адресата сохраняет PENDING.
- Повторная привязка того же адресата безопасна.
- Переназначение и самоприглашение запрещены.
- Чужой пользователь не может ответить.
- На границе истечения ответ отклоняется.
- Эффективный статус вычисляется без записи.
- expire не меняет ACCEPTED/REJECTED и не устанавливает respondedAt.
- Терминальное приглашение не возвращается в PENDING.
- Связь с самим собой не создаётся.

### 10.4 Application и integration tests

- Роль и профиль создаются атомарно через Workflows.
- Неизвестные коды отклоняются до сохранения; пустой набор ученика допустим.
- Смена pending и аннулирование прежнего verification атомарны.
- Старый токен не работает при возврате к тому же адресу.
- Verification одного типа профиля не изменяет другой.
- Подтверждение защищено от конкурентной смены адреса.
- Повтор события Identity не создаёт повторные последствия.
- Принятие и создание связи откатываются вместе при ошибке.
- Конкурентные принятия не создают дублирующую связь.
- Истёкший сохранённый PENDING не блокирует корректное новое приглашение после необходимой обработки в write-сценарии.
- Ошибка обработки уроков сохраняет связь при отвязке.
- Письмо инициируется надёжно, без SMTP-вызова из Domain.

### 10.5 Architecture tests

- Domain не зависит от Spring, security, чужих API или repositories.
- Агрегаты не наследуют аккаунт или публичные read-модели.
- Профили не содержат другие агрегаты.
- Коллекции не раскрываются как изменяемые.
- Время передаётся явно.
- Raw token не сохраняется; tokenHash не попадает в диагностический контекст доменных ошибок.

## 11. Последующая синхронизация и следующий этап

### 11.1 Уточнение этапов 1 и 2

В предыдущих документах требуется заменить общее правило «в каждом профиле минимум один предмет»:

```text
Преподаватель: минимум один предмет.
Ученик: предметы необязательны.
```

StudentProfileData.subjectCodes остаётся набором. Меняется ограничение: пустой набор допустим, null — нет.

Добавление внутренних SubjectSelection, ProfileType и verification-типов не требует само по себе изменения публичных Java-сигнатур.

Предыдущие документы не изменены в рамках сохранения этапа 3. До синхронизации данное уточнение этапа 3 имеет приоритет.

### 11.2 Что остаётся следующим этапам

На этапе Application определяются input/output ports, сервисы сценариев, mapping, авторизация, срок токенов, переотправка и координация транзакций.

На этапе Infrastructure определяются repositories, jOOQ mapping, ограничения БД, конкретная защита от гонок, генерация/хеширование токенов и доставка уведомлений.

Разработчику остаются private helpers, конструкторы восстановления, конкретный mapping-код, SQL DSL и организация test fixtures — без изменения зафиксированных доменных границ.

### 11.3 Фиксация этапа

Этап 3 утверждён пользователем 2026-09-16. Зафиксированы предметные области, единые model-пакеты, агрегаты, сущности, value objects, enum, операции, инварианты, доменные ошибки и требования к будущей реализации.

Следующий этап — подробное проектирование `tutoring.application`.
