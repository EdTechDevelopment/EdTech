# API-контракты v1.2.0

Текстовое описание целевого API Identity, Tutoring, Workflows и Scheduling. Машиночитаемая форма тех же контрактов: `scheduling.openapi.json`.

Текущий backend реализует Identity как частичный MVP-фундамент. Реализованы
`POST /auth/register`, verification/resend, login/refresh/logout и `PATCH /me`.
Составной `GET /me`, регистрация с обязательными Tutoring profiles, `birthDate`
и role onboarding отложены до модулей Tutoring/Workflows. Целевые профильные
схемы ниже не означают, что соответствующие endpoint уже реализованы.

## Общие соглашения

- Защищённые бизнес-endpoint'ы принимают access JWT в заголовке `Authorization: Bearer <token>`.
- Access JWT возвращается в `TokenResponse`, имеет короткий срок жизни и не передаётся через cookie.
- Refresh token не возвращается в JSON: сервер устанавливает его в HttpOnly cookie `REFRESH_TOKEN`.
- Cookie с refresh token отправляется только на `/auth/refresh` и `/auth/logout`; в production используется `Secure`, а `SameSite` и `Path` задаются серверной конфигурацией.
- Запросы с Bearer access token не используют отдельный CSRF-токен. Защита refresh-cookie endpoint'ов обеспечивается согласованными `SameSite`, CORS и проверкой Origin на сервере.
- Время: RFC 3339 с offset, ответы в UTC.
- Деньги: decimal-строка в RUB, например `"1500.00"`.
- Пагинация: `cursor + limit`, по умолчанию 50, максимум 100.
- Ошибка: `{code, message, fieldErrors, requestId}`.

## Endpoint'ы

Base URL: `/api/v1`. Ниже целевой составной API; существующий код Identity MVP ещё не реализует RegistrationWorkflow, Tutoring и Scheduling.

| Метод | URL | Назначение |
|---|---|---|
| `POST` | `/auth/register` | Атомарно создать аккаунт и выбранные профили; `Idempotency-Key` |
| `POST` | `/auth/email-verification/confirm`, `/auth/email-verification/resend` | Подтверждение account email |
| `POST` | `/auth/login`, `/auth/refresh`, `/auth/logout` | Аутентификация |
| `GET`, `PATCH` | `/me` | Свой аккаунт и профили / изменение аккаунта |
| `POST` | `/me/roles/teacher`, `/me/roles/student` | Добавить роль с профилем; `Idempotency-Key` |
| `GET` | `/tutoring/subjects` | Публичный справочник кодов предметов |
| `GET` | `/tutoring/profiles/teachers`, `/tutoring/profiles/students` | Публичный поиск активных профилей |
| `GET` | `/tutoring/profiles/teachers/{userId}`, `/tutoring/profiles/students/{userId}` | Публичная карточка активного профиля |
| `PUT` | `/tutoring/profiles/teacher`, `/tutoring/profiles/student` | Полностью обновить свой существующий профиль |
| `PUT` | `/tutoring/profiles/{type}/contact-email` | Запросить смену профильной почты |
| `POST` | `/tutoring/profiles/{type}/contact-email/confirmation-requests` | Повторить подтверждение current/pending |
| `POST` | `/tutoring/profile-email-confirmations` | Подтвердить профильный email токеном |
| `POST` | `/tutoring/invitations` | Пригласить по email; `Idempotency-Key` |
| `GET` | `/tutoring/invitations/sent`, `/tutoring/invitations/incoming` | Свои отправленные/входящие приглашения |
| `POST` | `/tutoring/invitations/{invitationId}/accept`, `/reject` | Ответить на приглашение; `Idempotency-Key` |
| `GET` | `/tutoring/relationships/students`, `/teachers` | Список подтверждённых связей |
| `GET`, `DELETE` | `/teachers/me/students/{studentUserId}` | Составная карточка со статистикой / отвязка через Workflows; для DELETE нужен ключ |
| `GET`, `POST`, `PATCH` | `/teachers/me/lessons...`, `/students/me/lessons...` | Будущий API Scheduling, описанный ниже |

## Важные инварианты

- Пользователь может иметь одну или обе роли. Для каждой роли существует ровно один соответствующий профиль; регистрация и добавление роли создают их атомарно.
- Регистрация содержит `birthDate`, обязательный профиль для каждой роли и отвечает `202 VerificationPendingResponse` без токенов до подтверждения account email.
- `Identity.User.birthDate` — первоисточник; при создании профиль Tutoring сохраняет копию. В публичной карточке активного профиля дата доступна без связи. Отдельного `BirthDateVisibility` нет.
- Публичный профиль показывает `displayName`, описание и обычные поля, `contactDetails`, выбранные предметы и только подтверждённый current `contactEmail`; pending и неподтверждённый адрес видит лишь владелец. Account email не подставляется.
- Справочник предметов используется для выбора и проверки кода. Выбранные предметы конкретного преподавателя/ученика хранятся и читаются только в его профиле. Учителю нужен минимум один, ученику разрешён пустой список.
- Приглашение остаётся `PENDING` до явного ответа. В отправленном списке не раскрывается ID адресата или факт регистрации.
- Публичность профиля не раскрывает чужие связи, уроки или статистику. Отвязка обрабатывает будущие уроки Scheduling до удаления связи Tutoring.
- Изменяющие межмодульные запросы и операции приглашения принимают стабильный `Idempotency-Key: UUID`; совпавший ключ с другим содержимым даёт `409`.
- Пароль принимается только в регистрации/login, 8–72 символа ASCII `!`…`~` без пробелов. Refresh token остаётся в HttpOnly cookie; access JWT в ответе после подтверждения account email/login.
- Ответ ошибки: `{code,message,fieldErrors,requestId}`. Время — RFC 3339 UTC, дата рождения — `YYYY-MM-DD`. Страницы — `cursor + limit` (50 по умолчанию, 1…100).

## Публичные схемы

| Схема | Основные поля |
|---|---|
| `RegisterRequest` | account-поля, `birthDate`, `roles`, соответствующие `teacherProfile`/`studentProfile` |
| `UserResponse` | account-поля Identity, включая `birthDate`, роли и статус |
| `MeResponse` | `user`, nullable `teacherProfile`, nullable `studentProfile`; роль без профиля — ошибка целостности |
| `TeacherProfileInput` / `StudentProfileInput` | `displayName`, `contactEmail`, `contactDetails`, `subjectCodes`, допустимые поля преподавателя/фото; без `birthDateVisibility` |
| `TeacherProfileUpdateRequest` / `StudentProfileUpdateRequest` | обычные поля, без изменения email и `birthDate` |
| `PublicTeacherProfileResponse` / `PublicStudentProfileResponse` | `userId`, `birthDate`, `displayName`, `contactDetails`, предметы, подтверждённый nullable `contactEmail`, фото и обычные поля роли |
| `PublicTeacherProfilePage` / `PublicStudentProfilePage` | `{items,nextCursor}` |
| `SubjectResponse` | `{subjectCode,name}`; `GET /tutoring/subjects` возвращает массив |
| `ProfileEmailChangeRequest` / `ProfileEmailConfirmationRequest` | `{newEmail}` / `{target: CURRENT\|PENDING}` |
| `SentInvitation` / `IncomingInvitation` | Отправленная запись не содержит recipient ID; входящая содержит краткое имя преподавателя |
| `InvitationDecisionResult` | `{invitationId,status}` после accept/reject |
| `LinkedStudentItem` / `LinkedTeacherItem` | `linkedAt` и публичные поля профиля; доступен только участникам связи |

Полные формы JSON и ошибки каждого endpoint находятся в `scheduling.openapi.json`; примеры — в `examples.json`.

## Контракты аккаунта

### `RegisterRequest`

Обязательные поля аккаунта: `email`, `password`, `firstName`, `lastName`, `birthDate`, `roles`. Для `TEACHER` обязателен `teacherProfile`, для `STUDENT` — `studentProfile`; при обеих ролях нужны оба. Лишний профиль или отсутствующий профиль для роли дают `ROLE_PROFILE_MISMATCH`. Выбранные предметы находятся в данных соответствующего профиля. Дата рождения передаётся один раз на уровне аккаунта и копируется в профиль доверенным workflow.

Повтор регистрации использует тот же `Idempotency-Key: UUID`. Успех — `202 VerificationPendingResponse`; никакого `201 MeResponse` или токенов на этом шаге нет.

### `TokenResponse`

```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1XzAwMiJ9.example-signature",
  "tokenType": "Bearer",
  "expiresInSeconds": 900
}
```

Refresh token отсутствует в JSON и доступен frontend только как автоматически отправляемая HttpOnly cookie. Frontend не должен пытаться прочитать эту cookie из JavaScript.

### `UserResponse`

```json
{
  "id": "u_002",
  "email": "anna@example.com",
  "pendingEmail": null,
  "firstName": "Анна",
  "lastName": "Петрова",
  "birthDate": "1995-05-12",
  "roles": ["STUDENT"],
  "status": "ACTIVE",
  "emailVerifiedAt": "2026-09-06T12:00:00Z",
  "createdAt": "2026-09-06T11:55:00Z",
  "updatedAt": "2026-09-06T12:00:00Z"
}
```

Все поля ответа обязательны. `pendingEmail` и `emailVerifiedAt` могут быть `null`. Множество ролей сериализуется в JSON-массив. `password`, `passwordHash`, access JWT и refresh token в ответ пользователя не входят.

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

`MeResponse.user` содержит данные аккаунта Identity. `teacherProfile` и `studentProfile` содержат self-view Tutoring без вложенного `user`; `null` допустим лишь при отсутствии соответствующей роли. Публичные карточки отличаются от self-view: в них нет pending email и неподтверждённого current email, зато дата рождения, обычные сведения и `contactDetails` доступны любому посетителю для ACTIVE профиля.

## Операции

### `POST /api/v1/auth/register`

Зарегистрировать пользователя.

Обязательны email, пароль, имя, фамилия, birthDate, минимум одна роль и заполненный профиль для каждой роли. `RegistrationWorkflow` атомарно создаёт Identity User со статусом PENDING_EMAIL_VERIFICATION и соответствующие профили Tutoring без access/refresh token.

**Авторизация:** Не требуется.

**Параметры:**

- Заголовок `Idempotency-Key: UUID` обязателен; точный повтор возвращает прежний результат.

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

- `200` — Email подтверждён. Выпущен access JWT и установлена refresh cookie.
- `400` — Ошибка формата или валидации.
- `409` — Конфликт состояния.
- `500` — Внутренняя ошибка.

### `POST /api/v1/auth/email-verification/resend`

Повторно отправить подтверждение.

Для `PENDING_EMAIL_VERIFICATION` и совпавшего текущего email создаёт новую
verification с purpose `REGISTRATION`. Для `ACTIVE` и совпавшего `pendingEmail`
создаёт `EMAIL_CHANGE`. Предыдущая активная verification того же purpose
инвалидируется. Неизвестный, уже подтверждённый текущий или неподходящий email
не создаёт письмо, но получает тот же нейтральный ответ.

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

- `200` — Выпущен access JWT и установлена refresh cookie.
- `400` — Ошибка формата или валидации.
- `401` — Не аутентифицирован.
- `403` — Действие запрещено.
- `429` — Слишком много запросов.
- `500` — Внутренняя ошибка.

### `POST /api/v1/auth/refresh`

Обновить access token.

Читает refresh token только из HttpOnly cookie, проверяет его и при включённой ротации устанавливает новую refresh cookie. Тело запроса отсутствует.

**Авторизация:** Refresh token в HttpOnly cookie `REFRESH_TOKEN`.

**Параметры:**

- Нет.

**Тело запроса:** Тело отсутствует.

**Ответы:**

- `200` — Выпущен новый access JWT.
- `401` — Не аутентифицирован.
- `403` — Действие запрещено.
- `500` — Внутренняя ошибка.

### `POST /api/v1/auth/logout`

Завершить текущую аутентификацию.

Идемпотентно отзывает всю session-family refresh token, переданного в HttpOnly
cookie, и удаляет cookie. Отсутствующая, неизвестная, просроченная или уже
отозванная cookie также считается успешно обработанной. Тело запроса отсутствует.

**Авторизация:** Опциональный refresh token в HttpOnly cookie `REFRESH_TOKEN`.

**Параметры:**

- Нет.

**Тело запроса:** Тело отсутствует.

**Ответы:**

- `204` — Session-family отозвана при её наличии, cookie удалена.
- `403` — Отсутствует разрешённый `Origin` или действие запрещено.
- `500` — Внутренняя ошибка.

### `GET /api/v1/me`

Получить текущего пользователя и профили.

**Статус реализации:** отложен до разработки модуля Tutoring. Identity уже
предоставляет внутренний публичный Java API `IdentityQuery.findUserById()`,
возвращающий безопасный `UserSummary`, но временный HTTP-ответ без профилей не
создаётся. Окончательный endpoint сразу будет соответствовать `MeResponse`.

**Авторизация:** Access JWT в заголовке `Authorization: Bearer <token>`.

**Параметры:**

- Нет.

**Тело запроса:** Тело отсутствует.

**Ответы:**

- `200` — Текущий пользователь.
- `401` — Не аутентифицирован.
- `500` — Внутренняя ошибка.

### `PATCH /api/v1/me`

Изменить общие данные текущего пользователя.

При смене email текущий адрес сохраняется в `email`, а новый записывается в
`pendingEmail` до подтверждения. Сервер создаёт verification с purpose
`EMAIL_CHANGE` на 5 минут и ставит письмо в очередь Notifications в той же
транзакции. Новый запрос с другим email заменяет прежний `pendingEmail` и
инвалидирует предыдущую активную verification. Передача текущего или уже
ожидающего email является идемпотентным no-op; для повторного письма используется
`POST /api/v1/auth/email-verification/resend`.

**Авторизация:** Access JWT в заголовке `Authorization: Bearer <token>`.

**Параметры:**

- Нет.

**Тело запроса:** `UpdateMeRequest`

**Ответы:**

- `200 UserResponse` — Данные обновлены. Account email остаётся прежним до подтверждения.
- `400` — Ошибка формата или валидации.
- `401` — Не аутентифицирован.
- `403` — Действие запрещено.
- `409` — Конфликт состояния.
- `500` — Внутренняя ошибка.

### Публичные предметы и профили Tutoring

`GET /api/v1/tutoring/subjects` доступен без login и отвечает `200` массивом `{subjectCode,name}`. Пустой справочник — внутренняя ошибка. Это список допустимых кодов, а не выбранные предметы конкретного человека.

`GET /api/v1/tutoring/profiles/teachers` и `/students` доступны без login. Фильтры: `subjectCode`, `minAge`, `maxAge`, `cursor`, `limit`. Возраст вычисляется по `birthDate` на дату запроса UTC; ответ `200 {items,nextCursor}` с ACTIVE профилями, сортировка `createdAt DESC,userId DESC`. Неверный фильтр/курсор — `400`. Фильтр предмета проверяет предметы, выбранные в самом профиле.

`GET /api/v1/tutoring/profiles/teachers/{userId}` и `/students/{userId}` возвращают публичный профиль активного пользователя или `404`. Связь и login не требуются. В ответе есть точная дата рождения, `contactDetails`, выбранные предметы и подтверждённый профильный email либо `null`.

### Обновление профиля и почты

`PUT /api/v1/tutoring/profiles/teacher` и `/student` требуют владельца, полностью заменяют обычные поля и отвечают `204`. Они не создают профиль, не меняют email-state или `birthDate`; отсутствие собственного профиля — `404`. Для Teacher `subjectCodes` непустой, для Student может быть пустым.

`PUT /api/v1/tutoring/profiles/{type}/contact-email` принимает `{newEmail}` и отвечает `200` состоянием смены. `POST /api/v1/tutoring/profiles/{type}/contact-email/confirmation-requests` принимает `{target:"CURRENT"|"PENDING"}` и отвечает `200`. Профильные письма ограничены пятью новыми письмами за скользящие 24 часа на пользователя; превышение — `429` с `Retry-After`.

`POST /api/v1/tutoring/profile-email-confirmations` без обязательного login принимает `{token}` в теле и отвечает `204`. Недействительный, использованный или истёкший токен даёт одинаковый `400`.

### Приглашения и связи

`POST /api/v1/tutoring/invitations` принимает `{studentEmail}` и `Idempotency-Key: UUID`; успех — `201 {invitationId,expiresAt}`. Известен адресат или нет, ответ этого не сообщает.

`GET /api/v1/tutoring/invitations/sent` и `/incoming` возвращают `{items,nextCursor}` с фильтром `status`, `cursor`, `limit`. Sent-элемент содержит email адресата и даты, но не его userId/факт регистрации. Incoming-элемент содержит краткое имя преподавателя. Просмотр входящего и отказ доступны адресату ещё до StudentProfile.

`POST /api/v1/tutoring/invitations/{invitationId}/accept` и `/reject` требуют `Idempotency-Key: UUID` и возвращают `200 {invitationId,status}`. Accept требует StudentProfile и атомарно создаёт связь; reject профиля не требует. Чужое и отсутствующее приглашение имеют одинаковый `404`.

`GET /api/v1/tutoring/relationships/students` и `/teachers` возвращают страницы собственных связей с `linkedAt` и публичными полями активного профиля. Сам список связей защищён, хотя содержимое отдельного активного профиля публично.

### Составные сценарии Workflows

`POST /api/v1/me/roles/teacher` и `/student` принимают профиль и `Idempotency-Key: UUID`, создают роль с профилем атомарно и отвечают `201 MeResponse`. После этого клиент обновляет access JWT через refresh.

`GET /api/v1/teachers/me/students/{studentUserId}` собирает публичные поля профиля и защищённую статистику уроков; статистика доступна только связанному преподавателю и требует Scheduling. `DELETE` по тому же адресу требует `Idempotency-Key: UUID`, сначала обрабатывает будущие уроки в Scheduling, затем удаляет связь Tutoring и отвечает `204`.

### `GET /api/v1/teachers/me/lessons`

Получить расписание репетитора.

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

Получить свой урок как репетитор.

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

## Примеры и TypeScript-типы

Готовые JSON-примеры находятся в `examples.json`, TypeScript DTO — в `contracts.ts`. Полные поля, ограничения, ответы и ошибки каждого endpoint'а описаны в `scheduling.openapi.json` и отображаются в Swagger UI.
