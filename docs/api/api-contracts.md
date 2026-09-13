# API-контракты v1.1.0

Источник требований — актуальная архитектура Identity и Tutoring. Машиночитаемый источник: `scheduling.openapi.json`.

## Общие соглашения

- Base URL: `/api/v1`.
- Защищённые endpoint'ы принимают access JWT в `Authorization: Bearer <token>`.
- Access JWT возвращается в `TokenResponse`; refresh token передаётся только в HttpOnly cookie `REFRESH_TOKEN`.
- Cookie действует на `/auth/refresh` и `/auth/logout`; в production используется `Secure`, а `SameSite` и `Path` задаются сервером.
- Время: RFC 3339 с offset, ответы в UTC. Дата рождения: строка `YYYY-MM-DD`.
- Деньги: decimal-строка в RUB, например `"1500.00"`.
- Пагинация: `cursor + limit`, по умолчанию 50, максимум 100.
- Ошибка: `{code, message, fieldErrors, requestId}`.

## Endpoint'ы

| Метод | URL | Назначение |
|---|---|---|
| `POST` | `/api/v1/auth/register` | Атомарно создать аккаунт, роли и первоначальные профили |
| `POST` | `/api/v1/auth/email-verification/confirm` | Подтвердить account email |
| `POST` | `/api/v1/auth/email-verification/resend` | Повторно отправить подтверждение account email |
| `POST` | `/api/v1/auth/login` | Войти |
| `POST` | `/api/v1/auth/refresh` | Обновить access token |
| `POST` | `/api/v1/auth/logout` | Завершить текущую аутентификацию |
| `GET` | `/api/v1/me` | Получить текущий аккаунт и профили |
| `PATCH` | `/api/v1/me` | Изменить данные аккаунта |
| `POST` | `/api/v1/me/roles/teacher` | Добавить роль TEACHER и TeacherProfile |
| `POST` | `/api/v1/me/roles/student` | Добавить роль STUDENT и StudentProfile |
| `GET` | `/api/v1/subjects` | Получить справочник предметов |
| `GET` | `/api/v1/teachers/me` | Получить свой TeacherProfile |
| `PUT` | `/api/v1/teachers/me` | Обновить существующий TeacherProfile |
| `POST` | `/api/v1/teachers/me/contact-email-verification` | Запросить изменение email TeacherProfile |
| `GET` | `/api/v1/students/me` | Получить свой StudentProfile |
| `PUT` | `/api/v1/students/me` | Обновить существующий StudentProfile |
| `POST` | `/api/v1/students/me/contact-email-verification` | Запросить изменение email StudentProfile |
| `POST` | `/api/v1/profile-email-verification/confirm` | Подтвердить email учебного профиля |
| `POST` | `/api/v1/teachers/me/student-invitations` | Пригласить ученика по email |
| `GET` | `/api/v1/students/me/invitations` | Получить входящие приглашения |
| `POST` | `/api/v1/students/me/invitations/{invitationId}/accept` | Принять приглашение |
| `POST` | `/api/v1/students/me/invitations/{invitationId}/reject` | Отклонить приглашение |
| `GET` | `/api/v1/teachers/me/students` | Получить своих учеников |
| `GET` | `/api/v1/teachers/me/students/{studentUserId}` | Получить карточку и статистику ученика |
| `DELETE` | `/api/v1/teachers/me/students/{studentUserId}` | Отвязать ученика и обработать будущие уроки |
| `GET` | `/api/v1/students/me/teachers` | Получить своих преподавателей |
| `GET` | `/api/v1/teachers/me/lessons` | Получить расписание преподавателя |
| `POST` | `/api/v1/teachers/me/lessons` | Создать урок |
| `GET` | `/api/v1/teachers/me/lessons/{lessonId}` | Получить свой урок как преподаватель |
| `PATCH` | `/api/v1/teachers/me/lessons/{lessonId}` | Изменить будущий урок |
| `POST` | `/api/v1/teachers/me/lessons/{lessonId}/cancel` | Отменить будущий урок |
| `POST` | `/api/v1/teachers/me/lessons/{lessonId}/status` | Отметить урок проведённым или пропущенным |
| `GET` | `/api/v1/students/me/lessons` | Получить расписание ученика |
| `GET` | `/api/v1/students/me/lessons/{lessonId}` | Получить свой урок как ученик |

## Важные инварианты

- При регистрации обязательны account data, `birthDate`, минимум одна роль и профиль каждой выбранной роли.
- `TEACHER` существует тогда и только тогда, когда существует `TeacherProfile`; для `STUDENT` действует то же правило.
- `RegistrationWorkflow` создаёт User, роли и профили атомарно.
- `birthDate` принадлежит Identity и не хранится в StudentProfile.
- `displayName` и `contactEmail` принадлежат каждому профилю Tutoring и не являются синонимами данных аккаунта.
- Profile email может совпадать с account email или отличаться; жизненные циклы изменения и подтверждения разделены.
- Регистрация не выдаёт access/refresh token до подтверждения account email.
- Подтверждение account email и login возвращают `TokenResponse` и устанавливают refresh cookie.
- `pendingEmail` содержит новый account email до подтверждения; `pendingContactEmail` содержит новый profile email.
- Вторая роль и профиль создаются только через `RoleOnboardingWorkflow`.
- Профиль в `MeResponse` nullable только при отсутствии соответствующей роли.
- Точная дата рождения другим пользователям не возвращается; nullable `age` вычисляется только при `LINKED_USERS`.
- Приглашение остаётся `PENDING` до явного принятия учеником.
- Индивидуальный урок содержит одного ученика, групповой — минимум двух.
- Уроки преподавателя и любого участника не пересекаются; соседние интервалы разрешены.

## Публичные схемы

| Схема | Вид | Основные поля или значения |
|---|---|---|
| `UserRole` | enum | `TEACHER`, `STUDENT` |
| `UserStatus` | enum | `PENDING_EMAIL_VERIFICATION`, `ACTIVE`, `SUSPENDED`, `DEACTIVATED` |
| `BirthDateVisibility` | enum | `PRIVATE`, `LINKED_USERS` |
| `UserResponse` | object | account fields, `birthDate`, roles, status, timestamps |
| `TeacherProfileInput` | object | `displayName`, `contactEmail`, contacts, subjects, teaching fields, visibility |
| `StudentProfileInput` | object | `displayName`, `contactEmail`, contacts, subjects, photo, visibility |
| `RegisterRequest` | oneOf | teacher-only, student-only или dual-role registration |
| `VerificationPendingResponse` | object | `email`, `verificationExpiresAt` |
| `ConfirmEmailRequest` | object | `token` |
| `LoginRequest` | object | `email`, `password` |
| `TokenResponse` | object | `accessToken`, `tokenType`, `expiresInSeconds` |
| `UpdateMeRequest` | object | `firstName?`, `lastName?`, `email?` |
| `TeacherProfileUpdateRequest` | object | редактируемые поля без `contactEmail` |
| `StudentProfileUpdateRequest` | object | редактируемые поля без `contactEmail` и `birthDate` |
| `TeacherProfileResponse` | object | `user` и собственные данные TeacherProfile |
| `StudentProfileResponse` | object | `user` и собственные данные StudentProfile |
| `ProfileEmailChangeRequest` | object | `email` |
| `ProfileEmailVerificationPendingResponse` | object | `profileRole`, `email`, `verificationExpiresAt` |
| `MeResponse` | object | `user`, `teacherProfile`, `studentProfile` |
| `InvitationStatus` | enum | `PENDING`, `ACCEPTED`, `REJECTED`, `EXPIRED` |
| `StudentInvitationResponse` | object | invitation target, profile teacher summary, state and timestamps |
| `StudentListItem` | object | `id`, `displayName`, `photoUrl`, `subjectCodes` |
| `StudentCardProfile` | object | `displayName`, confirmed `contactEmail?`, `age?`, subjects, photo |
| `StudentCardResponse` | object | `id`, `profile`, `statistics` |
| `TeacherContactResponse` | object | `id`, `displayName`, confirmed `contactEmail?`, profile fields |
| `LessonFormat` | enum | `INDIVIDUAL`, `GROUP` |
| `LocationType` | enum | `ONLINE`, `OFFLINE` |
| `LessonStatus` | enum | `SCHEDULED`, `COMPLETED`, `CANCELLED`, `MISSED` |
| `LessonCancelReason` | enum | `ILLNESS`, `FAMILY`, `NOT_READY`, `OTHER`, `STUDENT_UNLINKED` |
| `CreateLessonRequest` | oneOf | 8 взаимоисключающих вариантов |
| `UpdateLessonRequest` | object | изменяемые поля урока |
| `ApiError` | object | `code`, `message`, `fieldErrors`, `requestId` |

## Контракты аккаунта и профилей

### `RegisterRequest`

Пример регистрации ученика:

```json
{
  "email": "anna@example.com",
  "password": "example-password",
  "firstName": "Анна",
  "lastName": "Петрова",
  "birthDate": "2010-05-14",
  "roles": ["STUDENT"],
  "studentProfile": {
    "displayName": "Анна",
    "contactEmail": "anna@example.com",
    "contactDetails": [],
    "subjectCodes": ["MATH"],
    "photoUrl": null,
    "birthDateVisibility": "LINKED_USERS"
  }
}
```

Три допустимых варианта:

1. `roles: ["TEACHER"]` и только `teacherProfile`.
2. `roles: ["STUDENT"]` и только `studentProfile`.
3. Обе роли и оба профиля; порядок ролей не имеет значения.

### `UserResponse`

```json
{
  "id": "u_002",
  "email": "anna@example.com",
  "pendingEmail": null,
  "firstName": "Анна",
  "lastName": "Петрова",
  "birthDate": "2010-05-14",
  "roles": ["STUDENT"],
  "status": "ACTIVE",
  "emailVerifiedAt": "2026-09-06T12:00:00Z",
  "createdAt": "2026-09-06T11:55:00Z",
  "updatedAt": "2026-09-06T12:00:00Z"
}
```

`birthDate` возвращается владельцу аккаунта через `/me`. В первой версии `PATCH /me` её не изменяет.

### Профили

`displayName`, `contactEmail`, `pendingContactEmail`, `contactEmailVerifiedAt`, `contactDetails`, предметы и `birthDateVisibility` принадлежат Tutoring. Вложенный `user` в self-response принадлежит Identity и является результатом композиции.

Первоначальный профиль создаётся только внутри регистрации или role onboarding. `PUT /teachers/me` и `PUT /students/me` обновляют уже существующий профиль. Для изменения `contactEmail` используется отдельный verification flow.

## Операции

### `POST /api/v1/auth/register`

Атомарно создаёт Identity User и Tutoring profiles для выбранных ролей. Тело: `RegisterRequest`. Ответ `202 VerificationPendingResponse`. Возможны `400`, `409`, `429`, `500`.

### `POST /api/v1/auth/email-verification/confirm`

Подтверждает account email. Тело: `ConfirmEmailRequest`. Ответ `200 TokenResponse` и refresh cookie.

### `POST /api/v1/auth/email-verification/resend`

Повторно создаёт запрос подтверждения account email. Ответ `202` независимо от существования аккаунта.

### `POST /api/v1/auth/login`

Проверяет credentials и подтверждённый статус аккаунта. Ответ `200 TokenResponse` и refresh cookie.

### `POST /api/v1/auth/refresh`

Читает refresh token из HttpOnly cookie, возвращает новый access JWT и при необходимости ротирует cookie.

### `POST /api/v1/auth/logout`

Идемпотентно отзывает refresh token и очищает cookie. Ответ `204`.

### `GET /api/v1/me`

`MeQueryFacade` возвращает account `UserResponse` и профили выбранных ролей.

### `PATCH /api/v1/me`

Изменяет `firstName`, `lastName` и/или account email. Новый email остаётся `pendingEmail` до подтверждения. `birthDate` этим endpoint не меняется. Ответ: `200 UserResponse`.

### `POST /api/v1/me/roles/teacher`

Тело: `TeacherProfileInput`. `RoleOnboardingWorkflow` атомарно добавляет `TEACHER` и TeacherProfile. Ответ `201 MeResponse`; затем frontend вызывает `/auth/refresh`.

### `POST /api/v1/me/roles/student`

Тело: `StudentProfileInput`. `RoleOnboardingWorkflow` атомарно добавляет `STUDENT` и StudentProfile. Ответ `201 MeResponse`; затем frontend вызывает `/auth/refresh`.

### `GET /api/v1/subjects`

Возвращает `SubjectListResponse` аутентифицированному пользователю.

### `GET /api/v1/teachers/me`

Возвращает существующий TeacherProfile владельцу с ролью `TEACHER`.

### `PUT /api/v1/teachers/me`

Полностью заменяет редактируемые поля существующего TeacherProfile. Тело: `TeacherProfileUpdateRequest`. Profile email этим endpoint не изменяется.

### `GET /api/v1/students/me`

Возвращает существующий StudentProfile владельцу с ролью `STUDENT`.

### `PUT /api/v1/students/me`

Полностью заменяет редактируемые поля существующего StudentProfile. Тело: `StudentProfileUpdateRequest`. `birthDate` и profile email этим endpoint не изменяются.

### `POST /api/v1/teachers/me/contact-email-verification`

### `POST /api/v1/students/me/contact-email-verification`

Тело: `ProfileEmailChangeRequest`. Сохраняет новый адрес как `pendingContactEmail` и отвечает `202 ProfileEmailVerificationPendingResponse`. Текущий `contactEmail` продолжает действовать.

### `POST /api/v1/profile-email-verification/confirm`

Публичный endpoint подтверждает одноразовый token. После `204` pending адрес становится текущим profile email. Account email не изменяется.

### `POST /api/v1/teachers/me/student-invitations`

Пригласить ученика по email.

**Авторизация:** Access JWT в заголовке `Authorization: Bearer <token>`.

**Параметры:**

- Нет.

**Тело запроса:** `CreateStudentInvitationRequest`

**Ответы:**

- `201` — Приглашение создано на 30 суток.
- `400` — Ошибка формата или валидации.
- `401` — Не аутентифицирован.
- `403` — Действие запрещено.
- `409` — Конфликт состояния.
- `429` — Слишком много запросов.
- `500` — Внутренняя ошибка.

### `GET /api/v1/students/me/invitations`

Получить входящие приглашения.

**Авторизация:** Access JWT в заголовке `Authorization: Bearer <token>`.

**Параметры:**

- `status` (query, опциональный)
- `cursor` (query, опциональный)
- `limit` (query, опциональный, по умолчанию `50`)

**Тело запроса:** Тело отсутствует.

**Ответы:**

- `200` — Приглашения, createdAt DESC и id DESC.
- `400` — Ошибка формата или валидации.
- `401` — Не аутентифицирован.
- `403` — Действие запрещено.
- `500` — Внутренняя ошибка.

### `POST /api/v1/students/me/invitations/{invitationId}/accept`

Принять приглашение.

**Авторизация:** Access JWT в заголовке `Authorization: Bearer <token>`.

**Параметры:**

- `invitationId` (path, обязательный)

**Тело запроса:** Тело отсутствует.

**Ответы:**

- `204` — Связь создана.
- `401` — Не аутентифицирован.
- `403` — Действие запрещено.
- `404` — Ресурс не найден или скрыт.
- `409` — Конфликт состояния.
- `500` — Внутренняя ошибка.

### `POST /api/v1/students/me/invitations/{invitationId}/reject`

Отклонить приглашение.

**Авторизация:** Access JWT в заголовке `Authorization: Bearer <token>`.

**Параметры:**

- `invitationId` (path, обязательный)

**Тело запроса:** Тело отсутствует.

**Ответы:**

- `204` — Приглашение отклонено.
- `401` — Не аутентифицирован.
- `403` — Действие запрещено.
- `404` — Ресурс не найден или скрыт.
- `409` — Конфликт состояния.
- `500` — Внутренняя ошибка.

### `GET /api/v1/teachers/me/students`

Получить своих учеников.

**Авторизация:** Access JWT в заголовке `Authorization: Bearer <token>`.

**Параметры:**

- `cursor` (query, опциональный)
- `limit` (query, опциональный, по умолчанию `50`)

**Тело запроса:** Тело отсутствует.

**Ответы:**

- `200` — Ученики, lastName ASC, firstName ASC, id ASC.
- `400` — Ошибка формата или валидации.
- `401` — Не аутентифицирован.
- `403` — Действие запрещено.
- `500` — Внутренняя ошибка.

### `GET /api/v1/teachers/me/students/{studentUserId}`

Получить карточку и статистику ученика.

**Авторизация:** Access JWT в заголовке `Authorization: Bearer <token>`.

**Параметры:**

- `studentUserId` (path, обязательный)

**Тело запроса:** Тело отсутствует.

**Ответы:**

- `200` — Карточка связанного ученика.
- `401` — Не аутентифицирован.
- `403` — Действие запрещено.
- `404` — Ресурс не найден или скрыт.
- `500` — Внутренняя ошибка.

### `DELETE /api/v1/teachers/me/students/{studentUserId}`

Отвязать ученика и обработать будущие уроки.

Атомарно отменяет будущие индивидуальные уроки с причиной STUDENT_UNLINKED, удаляет ученика из будущих групповых уроков и меняет группу из одного участника на INDIVIDUAL.

**Авторизация:** Access JWT в заголовке `Authorization: Bearer <token>`.

**Параметры:**

- `studentUserId` (path, обязательный)

**Тело запроса:** Тело отсутствует.

**Ответы:**

- `204` — Ученик отвязан.
- `401` — Не аутентифицирован.
- `403` — Действие запрещено.
- `404` — Ресурс не найден или скрыт.
- `409` — Конфликт состояния.
- `500` — Внутренняя ошибка.

### `GET /api/v1/students/me/teachers`

Получить своих преподавательов.

**Авторизация:** Access JWT в заголовке `Authorization: Bearer <token>`.

**Параметры:**

- `cursor` (query, опциональный)
- `limit` (query, опциональный, по умолчанию `50`)

**Тело запроса:** Тело отсутствует.

**Ответы:**

- `200` — Репетиторы и их email.
- `400` — Ошибка формата или валидации.
- `401` — Не аутентифицирован.
- `403` — Действие запрещено.
- `500` — Внутренняя ошибка.

### `GET /api/v1/teachers/me/lessons`

Получить расписание преподавательа.

Окно [from,to), максимум 93 суток; startAt < to AND endAt > from. Сортировка startAt, id.

**Авторизация:** Access JWT в заголовке `Authorization: Bearer <token>`.

**Параметры:**

- `from` (query, обязательный)
- `to` (query, обязательный)
- `studentUserId` (query, опциональный)
- `subjectCode` (query, опциональный)
- `status` (query, опциональный)
- `sortOrder` (query, опциональный, по умолчанию `ASC`)
- `cursor` (query, опциональный)
- `limit` (query, опциональный, по умолчанию `50`)

**Тело запроса:** Тело отсутствует.

**Ответы:**

- `200` — Страница полного представления уроков.
- `400` — Ошибка формата или валидации.
- `401` — Не аутентифицирован.
- `403` — Действие запрещено.
- `500` — Внутренняя ошибка.

### `POST /api/v1/teachers/me/lessons`

Создать урок.

**Авторизация:** Access JWT в заголовке `Authorization: Bearer <token>`.

**Параметры:**

- Нет.

**Тело запроса:** `CreateLessonRequest`

**Ответы:**

- `201` — Урок создан со статусом SCHEDULED.
- `400` — Ошибка формата или валидации.
- `401` — Не аутентифицирован.
- `403` — Действие запрещено.
- `404` — Ресурс не найден или скрыт.
- `409` — Конфликт состояния.
- `500` — Внутренняя ошибка.

### `GET /api/v1/teachers/me/lessons/{lessonId}`

Получить свой урок как преподаватель.

**Авторизация:** Access JWT в заголовке `Authorization: Bearer <token>`.

**Параметры:**

- `lessonId` (path, обязательный)

**Тело запроса:** Тело отсутствует.

**Ответы:**

- `200` — Полное представление урока.
- `401` — Не аутентифицирован.
- `403` — Действие запрещено.
- `404` — Ресурс не найден или скрыт.
- `500` — Внутренняя ошибка.

### `PATCH /api/v1/teachers/me/lessons/{lessonId}`

Изменить будущий запланированный урок.

**Авторизация:** Access JWT в заголовке `Authorization: Bearer <token>`.

**Параметры:**

- `lessonId` (path, обязательный)

**Тело запроса:** `UpdateLessonRequest`

**Ответы:**

- `200` — Урок обновлён.
- `400` — Ошибка формата или валидации.
- `401` — Не аутентифицирован.
- `403` — Действие запрещено.
- `404` — Ресурс не найден или скрыт.
- `409` — Конфликт состояния.
- `500` — Внутренняя ошибка.

### `POST /api/v1/teachers/me/lessons/{lessonId}/cancel`

Отменить будущий урок.

**Авторизация:** Access JWT в заголовке `Authorization: Bearer <token>`.

**Параметры:**

- `lessonId` (path, обязательный)

**Тело запроса:** `CancelLessonRequest`

**Ответы:**

- `200` — Урок отменён с аудитом.
- `400` — Ошибка формата или валидации.
- `401` — Не аутентифицирован.
- `403` — Действие запрещено.
- `404` — Ресурс не найден или скрыт.
- `409` — Конфликт состояния.
- `500` — Внутренняя ошибка.

### `POST /api/v1/teachers/me/lessons/{lessonId}/status`

Отметить урок проведённым или пропущенным.

**Авторизация:** Access JWT в заголовке `Authorization: Bearer <token>`.

**Параметры:**

- `lessonId` (path, обязательный)

**Тело запроса:** `SetLessonStatusRequest`

**Ответы:**

- `200` — Статус изменён.
- `400` — Ошибка формата или валидации.
- `401` — Не аутентифицирован.
- `403` — Действие запрещено.
- `404` — Ресурс не найден или скрыт.
- `409` — Конфликт состояния.
- `500` — Внутренняя ошибка.

### `GET /api/v1/students/me/lessons`

Получить расписание ученика.

Окно [from,to), максимум 93 суток; startAt < to AND endAt > from. Не возвращает price и cancelledByUserId.

**Авторизация:** Access JWT в заголовке `Authorization: Bearer <token>`.

**Параметры:**

- `from` (query, обязательный)
- `to` (query, обязательный)
- `teacherUserId` (query, опциональный)
- `status` (query, опциональный)
- `sortOrder` (query, опциональный, по умолчанию `ASC`)
- `cursor` (query, опциональный)
- `limit` (query, опциональный, по умолчанию `50`)

**Тело запроса:** Тело отсутствует.

**Ответы:**

- `200` — Страница безопасного представления уроков.
- `400` — Ошибка формата или валидации.
- `401` — Не аутентифицирован.
- `403` — Действие запрещено.
- `500` — Внутренняя ошибка.

### `GET /api/v1/students/me/lessons/{lessonId}`

Получить свой урок как ученик.

**Авторизация:** Access JWT в заголовке `Authorization: Bearer <token>`.

**Параметры:**

- `lessonId` (path, обязательный)

**Тело запроса:** Тело отсутствует.

**Ответы:**

- `200` — Безопасное представление своего урока.
- `401` — Не аутентифицирован.
- `403` — Действие запрещено.
- `404` — Ресурс не найден или скрыт.
- `500` — Внутренняя ошибка.

## Примеры и генерируемые типы

Готовые JSON-примеры находятся в `examples.json`, TypeScript DTO — в `contracts.ts`. Полные поля, ограничения, ответы и ошибки каждого endpoint'а описаны в `scheduling.openapi.json` и отображаются в Swagger UI.
