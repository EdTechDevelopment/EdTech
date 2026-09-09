# API-контракты v1.0.0

Источник требований — утверждённое ТЗ. Машиночитаемый источник: `scheduling.openapi.json`.

## Общие соглашения

- Аутентификация: серверная cookie `SESSION`.
- Изменяющие защищённые запросы передают `X-XSRF-TOKEN` из cookie `XSRF-TOKEN`.
- Время: RFC 3339 с offset, ответы в UTC.
- Деньги: decimal-строка в RUB, например `"1500.00"`.
- Пагинация: `cursor + limit`, по умолчанию 50, максимум 100.
- Ошибка: `{code, message, fieldErrors, requestId}`.

## Endpoint'ы

| Метод | URL | Назначение |
|---|---|---|
| `POST` | `/api/v1/auth/register` | Зарегистрировать пользователя |
| `POST` | `/api/v1/auth/email-verification/confirm` | Подтвердить email |
| `POST` | `/api/v1/auth/email-verification/resend` | Повторно отправить подтверждение |
| `POST` | `/api/v1/auth/login` | Войти |
| `POST` | `/api/v1/auth/logout` | Завершить текущую сессию |
| `GET` | `/api/v1/me` | Получить текущего пользователя и профили |
| `PATCH` | `/api/v1/me` | Изменить общие данные текущего пользователя |
| `GET` | `/api/v1/subjects` | Получить справочник предметов |
| `GET` | `/api/v1/teachers/me` | Получить свой профиль репетитора |
| `PUT` | `/api/v1/teachers/me` | Создать или заменить свой профиль репетитора |
| `GET` | `/api/v1/students/me` | Получить свой профиль ученика |
| `PUT` | `/api/v1/students/me` | Создать или заменить свой профиль ученика |
| `POST` | `/api/v1/teachers/me/student-invitations` | Пригласить ученика по email |
| `GET` | `/api/v1/students/me/invitations` | Получить входящие приглашения |
| `POST` | `/api/v1/students/me/invitations/{invitationId}/accept` | Принять приглашение |
| `POST` | `/api/v1/students/me/invitations/{invitationId}/reject` | Отклонить приглашение |
| `GET` | `/api/v1/teachers/me/students` | Получить своих учеников |
| `GET` | `/api/v1/teachers/me/students/{studentUserId}` | Получить карточку и статистику ученика |
| `DELETE` | `/api/v1/teachers/me/students/{studentUserId}` | Отвязать ученика и обработать будущие уроки |
| `GET` | `/api/v1/students/me/teachers` | Получить своих репетиторов |
| `GET` | `/api/v1/teachers/me/lessons` | Получить расписание репетитора |
| `POST` | `/api/v1/teachers/me/lessons` | Создать урок |
| `GET` | `/api/v1/teachers/me/lessons/{lessonId}` | Получить свой урок как репетитор |
| `PATCH` | `/api/v1/teachers/me/lessons/{lessonId}` | Изменить будущий запланированный урок |
| `POST` | `/api/v1/teachers/me/lessons/{lessonId}/cancel` | Отменить будущий урок |
| `POST` | `/api/v1/teachers/me/lessons/{lessonId}/status` | Отметить урок проведённым или пропущенным |
| `GET` | `/api/v1/students/me/lessons` | Получить расписание ученика |
| `GET` | `/api/v1/students/me/lessons/{lessonId}` | Получить свой урок как ученик |

## Важные инварианты

- Пользователь может иметь обе роли и по одному профилю каждой роли.
- При регистрации обязательны email, пароль, имя, фамилия и минимум одна роль.
- Исходный пароль присутствует только в запросах регистрации и входа. API никогда не возвращает пароль или `passwordHash`.
- Регистрация не создаёт сессию до подтверждения email.
- `pendingEmail` содержит новый адрес, ожидающий подтверждения, либо `null`; это не логический флаг.
- Возраст не хранится в `User` и при необходимости вычисляется из `StudentProfile.birthDate`.
- `TeacherProfile` и `StudentProfile` связаны с аккаунтом по `userId` в доменной модели Tutoring. Вложенный `user` в DTO ответа — композиция данных для frontend, а не владение аккаунтом со стороны профиля.
- Приглашение остаётся `PENDING` до явного принятия учеником.
- Индивидуальный урок содержит одного ученика, групповой — минимум двух.
- Создание принимает `startAt` и ровно одно из `endAt`/`durationMinutes`.
- Уроки преподавателя и любого участника не пересекаются; соседние интервалы разрешены.
- Ученик не получает `price` и `cancelledByUserId`.
- Отвязка атомарно отменяет индивидуальные будущие уроки и изменяет состав групповых.

## Публичные схемы

Знак `?` обозначает опциональное поле. Nullable-поля могут присутствовать со значением `null`.

| Схема | Вид | Поля или значения |
|---|---|---|
| `UserRole` | enum | `TEACHER`, `STUDENT` |
| `UserStatus` | enum | `PENDING_EMAIL_VERIFICATION`, `ACTIVE` |
| `UserResponse` | object | `id`, `email`, `pendingEmail`, `firstName`, `lastName`, `roles`, `status`, `emailVerifiedAt`, `createdAt`, `updatedAt` |
| `UserSummary` | object | `id`, `firstName`, `lastName` |
| `TeacherSummary` | object | `id`, `firstName`, `lastName` |
| `TeacherContactSummary` | object | `id`, `email`, `firstName`, `lastName` |
| `StudentSummary` | object | `id`, `firstName`, `lastName` |
| `RegisterRequest` | object | `email`, `password`, `firstName`, `lastName`, `roles` |
| `VerificationPendingResponse` | object | `email`, `verificationExpiresAt` |
| `ConfirmEmailRequest` | object | `token` |
| `ResendEmailVerificationRequest` | object | `email` |
| `LoginRequest` | object | `email`, `password` |
| `UpdateMeRequest` | object | `firstName?`, `lastName?`, `email?` |
| `SubjectResponse` | object | `code`, `name` |
| `SubjectListResponse` | object | `items` |
| `TeacherProfileUpsertRequest` | object | `subjectCodes`, `description?`, `education?`, `experienceYears?`, `city?`, `photoUrl?` |
| `TeacherProfileResponse` | object | `user`, `subjectCodes`, `description`, `education`, `experienceYears`, `city`, `photoUrl` |
| `StudentProfileUpsertRequest` | object | `birthDate`, `subjectCodes`, `photoUrl?` |
| `StudentProfileResponse` | object | `user`, `birthDate`, `subjectCodes`, `photoUrl` |
| `MeResponse` | object | `user`, `teacherProfile`, `studentProfile` |
| `InvitationStatus` | enum | `PENDING`, `ACCEPTED`, `REJECTED`, `EXPIRED` |
| `CreateStudentInvitationRequest` | object | `email` |
| `StudentInvitationResponse` | object | `id`, `teacher`, `studentEmail`, `studentUserId`, `status`, `createdAt`, `expiresAt`, `respondedAt` |
| `StudentInvitationPage` | object | `items`, `nextCursor` |
| `StudentListItem` | object | `id`, `firstName`, `lastName`, `profileCompleted`, `photoUrl`, `subjectCodes` |
| `StudentPage` | object | `items`, `nextCursor` |
| `StudentCardProfile` | object | `birthDate`, `subjectCodes`, `photoUrl` |
| `StudentStatistics` | object | `totalLessons`, `completedLessons`, `cancelledLessons`, `missedLessons`, `lastLessonAt` |
| `StudentCardResponse` | object | `id`, `email`, `firstName`, `lastName`, `profile`, `statistics` |
| `TeacherContactResponse` | object | `id`, `email`, `firstName`, `lastName`, `profileCompleted`, `subjectCodes`, `description`, `photoUrl` |
| `TeacherContactPage` | object | `items`, `nextCursor` |
| `LessonFormat` | enum | `INDIVIDUAL`, `GROUP` |
| `LocationType` | enum | `ONLINE`, `OFFLINE` |
| `LessonStatus` | enum | `SCHEDULED`, `COMPLETED`, `CANCELLED`, `MISSED` |
| `LessonCancelReason` | enum | `ILLNESS`, `FAMILY`, `NOT_READY`, `OTHER`, `STUDENT_UNLINKED` |
| `LessonParticipantSummary` | alias | `StudentSummary` |
| `CreateLessonRequest` | oneOf | 8 взаимоисключающих вариантов |
| `UpdateLessonRequest` | object | `studentUserIds?`, `subjectCode?`, `format?`, `locationType?`, `meetingUrl?`, `offlineAddress?`, `price?`, `startAt?`, `endAt?`, `durationMinutes?` |
| `TeacherLessonResponse` | oneOf | 4 взаимоисключающих вариантов |
| `StudentLessonResponse` | oneOf | 4 взаимоисключающих вариантов |
| `TeacherLessonPage` | object | `items`, `nextCursor` |
| `StudentLessonPage` | object | `items`, `nextCursor` |
| `CancelLessonRequest` | object | `reason`, `comment?` |
| `SetLessonStatusRequest` | object | `status` |
| `FieldError` | object | `field`, `code`, `message` |
| `ErrorCode` | enum | `VALIDATION_ERROR`, `UNKNOWN_SUBJECT`, `UNAUTHENTICATED`, `INVALID_CREDENTIALS`, `EMAIL_NOT_VERIFIED`, `FORBIDDEN`, `NOT_FOUND`, `PROFILE_NOT_FOUND`, `EMAIL_ALREADY_EXISTS`, `INVALID_VERIFICATION_TOKEN`, `INVITATION_ALREADY_PENDING`, `INVITATION_EXPIRED`, `INVALID_INVITATION_STATE`, `ALREADY_LINKED`, `STUDENT_NOT_LINKED`, `LESSON_OVERLAP`, `INVALID_LESSON_STATE`, `RATE_LIMIT_EXCEEDED`, `INTERNAL_ERROR` |
| `ApiError` | object | `code`, `message`, `fieldErrors`, `requestId` |

## Контракты аккаунта

### `RegisterRequest`

Все поля обязательны. `roles` содержит одну или обе роли без повторений.

```json
{
  "email": "anna@example.com",
  "password": "example-password",
  "firstName": "Анна",
  "lastName": "Петрова",
  "roles": ["STUDENT"]
}
```

| Поле | Тип | Ограничения |
|---|---|---|
| `email` | string | Корректный email, максимум 254 символа, уникален без учёта регистра |
| `password` | string | От 8 до 128 символов, только для записи |
| `firstName` | string | От 1 до 100 символов после `trim` |
| `lastName` | string | От 1 до 100 символов после `trim` |
| `roles` | `UserRole[]` | От 1 до 2 уникальных значений: `TEACHER`, `STUDENT` |

После регистрации сервер хранит bcrypt-хэш пароля, создаёт пользователя со статусом `PENDING_EMAIL_VERIFICATION`, отправляет письмо и отвечает `202 VerificationPendingResponse`. Сессия появляется только после подтверждения email.

### `UserResponse`

```json
{
  "id": "u_002",
  "email": "anna@example.com",
  "pendingEmail": null,
  "firstName": "Анна",
  "lastName": "Петрова",
  "roles": ["STUDENT"],
  "status": "ACTIVE",
  "emailVerifiedAt": "2026-09-06T12:00:00Z",
  "createdAt": "2026-09-06T11:55:00Z",
  "updatedAt": "2026-09-06T12:00:00Z"
}
```

Все поля ответа обязательны. `pendingEmail` и `emailVerifiedAt` могут быть `null`. Множество ролей сериализуется в JSON-массив. `password`, `passwordHash`, токены и сведения о серверной сессии в ответ не входят.

### `UpdateMeRequest`

Запрос содержит хотя бы одно поле из `firstName`, `lastName`, `email`:

```json
{
  "firstName": "Анна-Мария",
  "email": "anna.new@example.com"
}
```

Имя и фамилия меняются сразу. Новый email записывается в `pendingEmail`, а поле `email` сохраняет прежнее значение до подтверждения. После подтверждения `email` получает новый адрес, `pendingEmail` становится `null`, `emailVerifiedAt` и `updatedAt` обновляются. Роли и пароль этим запросом не изменяются.

### Профили

`TeacherProfile` и `StudentProfile` принадлежат модулю Tutoring и связаны с `User` через его идентификатор. Email, имя, фамилия, роли, состояние аккаунта и пароль в профилях не хранятся. `TeacherProfileResponse` и `StudentProfileResponse` включают `user: UserResponse`, чтобы frontend получил готовую составную модель одним запросом.

## Операции

### `POST /api/v1/auth/register`

Зарегистрировать пользователя.

Обязательны email, пароль, имя, фамилия и минимум одна роль. Создаёт аккаунт PENDING_EMAIL_VERIFICATION без сессии.

**Авторизация:** Не требуется.

**Параметры:**

- Нет.

**Тело запроса:** `RegisterRequest`

**Ответы:**

- `202` — Создан неподтверждённый аккаунт.
- `400` — Ошибка формата или валидации.
- `409` — Конфликт состояния.
- `429` — Слишком много запросов.
- `500` — Внутренняя ошибка.

### `POST /api/v1/auth/email-verification/confirm`

Подтвердить email.

**Авторизация:** Не требуется.

**Параметры:**

- Нет.

**Тело запроса:** `ConfirmEmailRequest`

**Ответы:**

- `200` — Email подтверждён, сессия создана.
- `400` — Ошибка формата или валидации.
- `409` — Конфликт состояния.
- `500` — Внутренняя ошибка.

### `POST /api/v1/auth/email-verification/resend`

Повторно отправить подтверждение.

**Авторизация:** Не требуется.

**Параметры:**

- Нет.

**Тело запроса:** `ResendEmailVerificationRequest`

**Ответы:**

- `202` — Запрос принят независимо от существования аккаунта.
- `400` — Ошибка формата или валидации.
- `429` — Слишком много запросов.
- `500` — Внутренняя ошибка.

### `POST /api/v1/auth/login`

Войти.

**Авторизация:** Не требуется.

**Параметры:**

- Нет.

**Тело запроса:** `LoginRequest`

**Ответы:**

- `200` — Сессия создана.
- `400` — Ошибка формата или валидации.
- `401` — Не аутентифицирован.
- `403` — Действие запрещено.
- `429` — Слишком много запросов.
- `500` — Внутренняя ошибка.

### `POST /api/v1/auth/logout`

Завершить текущую сессию.

**Авторизация:** Серверная сессия `SESSION`.

**Параметры:**

- `X-XSRF-TOKEN` (header, обязательный)

**Тело запроса:** Тело отсутствует.

**Ответы:**

- `204` — Сессия завершена.
- `401` — Не аутентифицирован.
- `403` — Действие запрещено.
- `500` — Внутренняя ошибка.

### `GET /api/v1/me`

Получить текущего пользователя и профили.

**Авторизация:** Серверная сессия `SESSION`.

**Параметры:**

- Нет.

**Тело запроса:** Тело отсутствует.

**Ответы:**

- `200` — Текущий пользователь.
- `401` — Не аутентифицирован.
- `500` — Внутренняя ошибка.

### `PATCH /api/v1/me`

Изменить общие данные текущего пользователя.

При смене email текущий адрес сохраняется в email, а новый записывается в pendingEmail до подтверждения.

**Авторизация:** Серверная сессия `SESSION`.

**Параметры:**

- `X-XSRF-TOKEN` (header, обязательный)

**Тело запроса:** `UpdateMeRequest`

**Ответы:**

- `200` — Данные обновлены.
- `400` — Ошибка формата или валидации.
- `401` — Не аутентифицирован.
- `403` — Действие запрещено.
- `409` — Конфликт состояния.
- `500` — Внутренняя ошибка.

### `GET /api/v1/subjects`

Получить справочник предметов.

**Авторизация:** Серверная сессия `SESSION`.

**Параметры:**

- Нет.

**Тело запроса:** Тело отсутствует.

**Ответы:**

- `200` — Активные предметы.
- `401` — Не аутентифицирован.
- `500` — Внутренняя ошибка.

### `GET /api/v1/teachers/me`

Получить свой профиль репетитора.

**Авторизация:** Серверная сессия `SESSION`.

**Параметры:**

- Нет.

**Тело запроса:** Тело отсутствует.

**Ответы:**

- `200` — Профиль репетитора.
- `401` — Не аутентифицирован.
- `403` — Действие запрещено.
- `404` — Ресурс не найден или скрыт.
- `500` — Внутренняя ошибка.

### `PUT /api/v1/teachers/me`

Создать или заменить свой профиль репетитора.

**Авторизация:** Серверная сессия `SESSION`.

**Параметры:**

- `X-XSRF-TOKEN` (header, обязательный)

**Тело запроса:** `TeacherProfileUpsertRequest`

**Ответы:**

- `200` — Профиль обновлён.
- `201` — Профиль создан.
- `400` — Ошибка формата или валидации.
- `401` — Не аутентифицирован.
- `403` — Действие запрещено.
- `500` — Внутренняя ошибка.

### `GET /api/v1/students/me`

Получить свой профиль ученика.

**Авторизация:** Серверная сессия `SESSION`.

**Параметры:**

- Нет.

**Тело запроса:** Тело отсутствует.

**Ответы:**

- `200` — Профиль ученика.
- `401` — Не аутентифицирован.
- `403` — Действие запрещено.
- `404` — Ресурс не найден или скрыт.
- `500` — Внутренняя ошибка.

### `PUT /api/v1/students/me`

Создать или заменить свой профиль ученика.

**Авторизация:** Серверная сессия `SESSION`.

**Параметры:**

- `X-XSRF-TOKEN` (header, обязательный)

**Тело запроса:** `StudentProfileUpsertRequest`

**Ответы:**

- `200` — Профиль обновлён.
- `201` — Профиль создан.
- `400` — Ошибка формата или валидации.
- `401` — Не аутентифицирован.
- `403` — Действие запрещено.
- `500` — Внутренняя ошибка.

### `POST /api/v1/teachers/me/student-invitations`

Пригласить ученика по email.

**Авторизация:** Серверная сессия `SESSION`.

**Параметры:**

- `X-XSRF-TOKEN` (header, обязательный)

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

**Авторизация:** Серверная сессия `SESSION`.

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

**Авторизация:** Серверная сессия `SESSION`.

**Параметры:**

- `invitationId` (path, обязательный)
- `X-XSRF-TOKEN` (header, обязательный)

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

**Авторизация:** Серверная сессия `SESSION`.

**Параметры:**

- `invitationId` (path, обязательный)
- `X-XSRF-TOKEN` (header, обязательный)

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

**Авторизация:** Серверная сессия `SESSION`.

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

**Авторизация:** Серверная сессия `SESSION`.

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

**Авторизация:** Серверная сессия `SESSION`.

**Параметры:**

- `studentUserId` (path, обязательный)
- `X-XSRF-TOKEN` (header, обязательный)

**Тело запроса:** Тело отсутствует.

**Ответы:**

- `204` — Ученик отвязан.
- `401` — Не аутентифицирован.
- `403` — Действие запрещено.
- `404` — Ресурс не найден или скрыт.
- `409` — Конфликт состояния.
- `500` — Внутренняя ошибка.

### `GET /api/v1/students/me/teachers`

Получить своих репетиторов.

**Авторизация:** Серверная сессия `SESSION`.

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

Получить расписание репетитора.

Окно [from,to), максимум 93 суток; startAt < to AND endAt > from. Сортировка startAt, id.

**Авторизация:** Серверная сессия `SESSION`.

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

**Авторизация:** Серверная сессия `SESSION`.

**Параметры:**

- `X-XSRF-TOKEN` (header, обязательный)

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

Получить свой урок как репетитор.

**Авторизация:** Серверная сессия `SESSION`.

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

**Авторизация:** Серверная сессия `SESSION`.

**Параметры:**

- `lessonId` (path, обязательный)
- `X-XSRF-TOKEN` (header, обязательный)

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

**Авторизация:** Серверная сессия `SESSION`.

**Параметры:**

- `lessonId` (path, обязательный)
- `X-XSRF-TOKEN` (header, обязательный)

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

**Авторизация:** Серверная сессия `SESSION`.

**Параметры:**

- `lessonId` (path, обязательный)
- `X-XSRF-TOKEN` (header, обязательный)

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

**Авторизация:** Серверная сессия `SESSION`.

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

**Авторизация:** Серверная сессия `SESSION`.

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
