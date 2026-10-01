# Frontend-контракты v1.2.0

> Статус: это целевой составной контракт frontend для Identity + Tutoring.
> Текущий Identity MVP реализует account-only `POST /auth/register` без
> `birthDate` и Tutoring profiles, а `GET /me` отложен. Использовать целевой
> registration/`MeResponse` контракт можно после появления RegistrationWorkflow
> и Tutoring; временные несовместимые frontend DTO не создаются.

## Подключение

- Base URL: `/api/v1`.
- После получения access token frontend передаёт его в `Authorization: Bearer <token>` для защищённых запросов.
- Access token хранится в памяти приложения и не записывается в `localStorage` или доступную JavaScript cookie.
- Login, подтверждение email, refresh и logout отправляются с `credentials: "include"`.
- При `401` frontend один раз вызывает `POST /auth/refresh`, заменяет access token и повторяет исходный запрос.
- Если refresh отвечает `401`, frontend очищает локальное состояние аутентификации и открывает экран входа.
- При `403 EMAIL_NOT_VERIFIED` frontend показывает повторную отправку письма.

## Основные потоки

1. Регистрация отправляет данные аккаунта и один или два обязательных профиля одним запросом.
2. После регистрации frontend показывает ожидание письма подтверждения account email.
3. Подтверждение account email возвращает `TokenResponse`, устанавливает refresh cookie, затем frontend вызывает `GET /me`.
4. `GET /me` возвращает аккаунт и профили. Для каждой роли соответствующий профиль обязан существовать.
5. Добавление второй роли отправляет профиль в role-onboarding endpoint. После успеха frontend обновляет access JWT через `/auth/refresh`.
6. Изменение profile email выполняется отдельным verification flow и не изменяет account email.
7. Приглашение всегда принимает ученик; регистрация только связывает его с подтверждённым account email.
8. Календарь запрашивает диапазон `[from,to)`, сохраняет `nextCursor` и не разбирает курсор.
9. Поиск активных профилей доступен без входа; предметы и возраст берутся из публичных профильных данных.

`GET /me` и составной `MeResponse` пока не реализованы: endpoint будет завершён
одновременно с read API профилей Tutoring. Identity уже имеет межмодульный
`IdentityQuery`, но он является Java-контрактом backend и не является временной
HTTP-ручкой для frontend.

## Регистрация

`POST /auth/register` обслуживает `RegistrationWorkflow`. Он атомарно создаёт Identity User, роли и соответствующие профили Tutoring.

```ts
type StudentProfileInput = {
  displayName: string;
  contactEmail: string;
  contactDetails: Array<string>;
  subjectCodes: Array<string>;
  photoUrl?: string | null;
};

type StudentRegistrationRequest = {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  birthDate: string;
  roles: ["STUDENT"];
  studentProfile: StudentProfileInput;
};
```

`RegisterRequest` является объединением трёх вариантов:

- только `TEACHER` и обязательный `teacherProfile`;
- только `STUDENT` и обязательный `studentProfile`;
- обе роли и оба обязательных профиля.

Лишний профиль и роль без профиля отклоняются с `ROLE_PROFILE_MISMATCH`. `birthDate` принадлежит аккаунту Identity. `displayName` и `contactEmail` принадлежат конкретному профилю Tutoring.

`contactEmail` может совпадать с account `email` или содержать другой адрес. Совпадающий адрес отмечается подтверждённым после `AccountEmailVerifiedEvent`; другой адрес требует отдельного подтверждения.

Успешная регистрация отвечает `202 VerificationPendingResponse` и не выдаёт токены до подтверждения account email.
Запрос содержит обязательный `Idempotency-Key: UUID`: при сетевом повторе используется тот же ключ. Профили и аккаунт создаются вместе либо не создаются вовсе.

## Повторная отправка подтверждения

Если confirm отвечает `INVALID_VERIFICATION_TOKEN`, frontend показывает
нейтральное сообщение о недействительной или просроченной ссылке, поле email и
кнопку повторной отправки. Email можно предварительно заполнить из результата
регистрации, но пользователь должен иметь возможность ввести его вручную —
например, если ссылка открыта в другом браузере.

`POST /auth/email-verification/resend` всегда отвечает `202` для корректно
сформированного email. Интерфейс показывает сообщение: «Если этот адрес ожидает
подтверждения, мы отправили новое письмо». Ответ не подтверждает существование
аккаунта. Новая ссылка заменяет предыдущую активную ссылку того же purpose.

## JWT-аутентификация

```ts
type TokenResponse = {
  accessToken: string;
  tokenType: "Bearer";
  expiresInSeconds: number;
};
```

Refresh token отсутствует в JSON и недоступен JavaScript. Браузер получает его через `Set-Cookie` после login или подтверждения account email и отправляет автоматически на `POST /auth/refresh` и `POST /auth/logout`.

Logout идемпотентен: frontend принимает `204` как успешное завершение даже при
отсутствующей или уже недействительной refresh cookie, удаляет access token из
памяти и открывает экран входа. Уже выпущенный access JWT сервер не отзывает и он
может действовать до собственного `exp`.

Frontend не запускает несколько параллельных refresh-запросов после одновременных `401`. Используется один общий refresh promise, после которого ожидающие запросы повторяются не более одного раза.

## Текущий пользователь

```ts
type UserResponse = {
  id: string;
  email: string;
  pendingEmail: string | null;
  firstName: string;
  lastName: string;
  birthDate: string;
  roles: Array<"TEACHER" | "STUDENT">;
  status: "PENDING_EMAIL_VERIFICATION" | "ACTIVE" | "SUSPENDED" | "DEACTIVATED";
  emailVerifiedAt: string | null;
  createdAt: string;
  updatedAt: string;
};
```

`birthDate` возвращается владельцу аккаунта через `GET /me`. В первой версии она не изменяется через `PATCH /me`.

`PATCH /me` принимает хотя бы одно из полей `firstName`, `lastName`, `email` и возвращает `UserResponse`. После запроса смены account email старый адрес остаётся в `email`, а новый приходит в `pendingEmail` до подтверждения. Первое письмо создаётся самим `PATCH /me`; если письмо нужно отправить повторно, frontend вызывает `/auth/email-verification/resend`. Повторный `PATCH` с тем же текущим или уже ожидающим email новое письмо не создаёт.

## Профили и роли

`MeResponse` всегда содержит `user`. `teacherProfile` равен `null`, только если у пользователя нет роли `TEACHER`; `studentProfile` равен `null`, только если нет роли `STUDENT`. Роль с отсутствующим профилем считается серверной ошибкой согласованности.

Профиль хранит собственные `displayName`, `contactEmail`, `pendingContactEmail`, `contactEmailVerifiedAt`, `contactDetails` и выбранные предметы. `birthDate` копируется из Identity при создании профиля. В `MeResponse` данные аккаунта находятся в `user`, а профили — в соседних `teacherProfile`/`studentProfile`; внутри профилей повторного `user` нет.

Существующий профиль обновляется через:

```text
PUT /api/v1/tutoring/profiles/teacher
PUT /api/v1/tutoring/profiles/student
```

Эти endpoint не создают профиль и не изменяют `contactEmail`.

Вторая роль вместе с первоначальным профилем создаётся атомарно через:

```text
POST /me/roles/teacher
POST /me/roles/student
```

После ответа `201 MeResponse` frontend вызывает `POST /auth/refresh`, чтобы получить JWT с новым набором ролей.
Оба запроса на добавление роли требуют стабильный `Idempotency-Key: UUID`.

## Подтверждение profile email

Запрос изменения адреса:

```text
PUT /api/v1/tutoring/profiles/{type}/contact-email
```

Тело содержит `{ "newEmail": "new.profile@example.com" }`. Текущий подтверждённый `contactEmail` продолжает действовать, а новый адрес появляется в `pendingContactEmail`. Повторная проверка current/pending выполняется через `POST /api/v1/tutoring/profiles/{type}/contact-email/confirmation-requests` с `{ "target": "CURRENT" | "PENDING" }`.

Ссылка из письма передаёт токен в:

```text
POST /api/v1/tutoring/profile-email-confirmations
```

После успешного ответа `204` новый адрес становится `contactEmail`, `pendingContactEmail` очищается, а `contactEmailVerifiedAt` обновляется.

## Публичные профили, контакты и возраст

- `GET /api/v1/tutoring/profiles/teachers` и `/students` дают публичный поиск активных профилей с фильтрами `subjectCode`, `minAge`, `maxAge`, `cursor`, `limit`; ответ `{items,nextCursor}`. Выбранные предметы берутся из профиля.
- `GET /api/v1/tutoring/profiles/teachers/{userId}` и `/students/{userId}` дают публичную карточку активного профиля без login и без связи.
- Карточка содержит точную `birthDate`, `displayName`, `contactDetails`, выбранные предметы, фото и обычные поля профиля. Возраст вычисляется из даты рождения; отдельного сохранённого `age` нет.
- `contactEmail` виден всем только после подтверждения. Неподтверждённый и pending адрес видит только владелец. Account email не подставляется вместо профильного.
- Учебные списки и уроки используют профильный `displayName`, а не `Identity.firstName + lastName`. Список связей и статистика уроков требуют отдельного права доступа; публичность профиля их не открывает.
- `GET /api/v1/tutoring/subjects` возвращает массив `{subjectCode,name}` без login. Справочник помогает выбрать допустимый код; предметы конкретного человека читаются из его профиля.

## Видимость урока

| Поле | Преподаватель | Ученик |
|---|---:|---:|
| Участники своего урока | Да | Да |
| Подтверждённый profile email преподавателя | Собственный профиль | Да |
| Место или ссылка | Да | Да |
| Причина и комментарий отмены | Да | Да |
| Цена | Да | Нет |
| `cancelledByUserId` | Да | Нет |

Использовать типы из `contracts.ts` и сверять их с `scheduling.openapi.json` версии `1.2.0`.
