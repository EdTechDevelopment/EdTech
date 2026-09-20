# Frontend-контракты v1.1.0

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

## Регистрация

`POST /auth/register` обслуживает `RegistrationWorkflow`. Он атомарно создаёт Identity User, роли и соответствующие профили Tutoring.

```ts
type BirthDateVisibility = "PRIVATE" | "LINKED_USERS";

type StudentProfileInput = {
  displayName: string;
  contactEmail: string;
  contactDetails: Array<string>;
  subjectCodes: Array<string>;
  photoUrl?: string | null;
  birthDateVisibility: BirthDateVisibility;
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

## JWT-аутентификация

```ts
type TokenResponse = {
  accessToken: string;
  tokenType: "Bearer";
  expiresInSeconds: number;
};
```

Refresh token отсутствует в JSON и недоступен JavaScript. Браузер получает его через `Set-Cookie` после login или подтверждения account email и отправляет автоматически на `POST /auth/refresh` и `POST /auth/logout`.

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

`PATCH /me` принимает хотя бы одно из полей `firstName`, `lastName`, `email` и возвращает `UserResponse`. После запроса смены account email старый адрес остаётся в `email`, а новый приходит в `pendingEmail` до подтверждения.

## Профили и роли

`MeResponse` всегда содержит `user`. `teacherProfile` равен `null`, только если у пользователя нет роли `TEACHER`; `studentProfile` равен `null`, только если нет роли `STUDENT`. Роль с отсутствующим профилем считается серверной ошибкой согласованности.

Профиль хранит собственные `displayName`, `contactEmail`, `pendingContactEmail`, `contactEmailVerifiedAt`, `contactDetails`, предметы и настройку `birthDateVisibility`. Вложенный `user` является составным представлением данных Identity и не меняет владельца профильных данных.

Существующий профиль обновляется через:

```text
PUT /teachers/me
PUT /students/me
```

Эти endpoint не создают профиль и не изменяют `contactEmail`.

Вторая роль вместе с первоначальным профилем создаётся атомарно через:

```text
POST /me/roles/teacher
POST /me/roles/student
```

После ответа `201 MeResponse` frontend вызывает `POST /auth/refresh`, чтобы получить JWT с новым набором ролей.

## Подтверждение profile email

Запрос изменения адреса:

```text
POST /teachers/me/contact-email-verification
POST /students/me/contact-email-verification
```

Тело содержит `{ "email": "new.profile@example.com" }`. Текущий подтверждённый `contactEmail` продолжает действовать, а новый адрес появляется в `pendingContactEmail`.

Ссылка из письма передаёт токен в:

```text
POST /profile-email-verification/confirm
```

После успешного ответа `204` новый адрес становится `contactEmail`, `pendingContactEmail` очищается, а `contactEmailVerifiedAt` обновляется.

## Имена, контакты и возраст в составных ответах

- Учебные списки и уроки используют профильный `displayName`, а не `Identity.firstName + lastName`.
- Другому связанному пользователю возвращается только подтверждённый `contactEmail` или явно разрешённый fallback account email.
- Точная `birthDate` другим пользователям не возвращается.
- Карточка ученика содержит nullable `age`, вычисленный query facade из Identity birth date только при `LINKED_USERS`.

## Видимость урока

| Поле | Преподаватель | Ученик |
|---|---:|---:|
| Участники своего урока | Да | Да |
| Подтверждённый profile email преподавателя | Собственный профиль | Да |
| Место или ссылка | Да | Да |
| Причина и комментарий отмены | Да | Да |
| Цена | Да | Нет |
| `cancelledByUserId` | Да | Нет |

Использовать типы из `contracts.ts`; фактическим источником ограничений остаётся `scheduling.openapi.json` версии `1.1.0`.
