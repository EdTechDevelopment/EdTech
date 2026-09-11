# Frontend-контракты v1.0.0

## Подключение

- Base URL: `/api/v1`.
- После получения access token frontend передаёт его в `Authorization: Bearer <token>` для всех защищённых бизнес-запросов.
- Access token хранится в памяти приложения и не записывается в `localStorage` или доступную JavaScript cookie.
- Запросы login, подтверждения email, refresh и logout отправляются с `credentials: "include"`, чтобы браузер установил или передал HttpOnly refresh cookie.
- При `401` на защищённом запросе frontend один раз вызывает `POST /auth/refresh`, заменяет access token и повторяет исходный запрос.
- Если refresh отвечает `401`, frontend очищает локальное состояние аутентификации и открывает экран входа.
- При `403 EMAIL_NOT_VERIFIED` frontend показывает повторную отправку письма.

## Основные потоки

1. Регистрация → экран ожидания письма → подтверждение → `TokenResponse` и refresh cookie → `GET /me`.
2. Login → `TokenResponse` и refresh cookie → `GET /me`.
3. Refresh → новый `TokenResponse`; браузер автоматически отправляет refresh cookie.
4. `GET /me` определяет роли и необходимость онбординга по значениям профилей `null`.
5. Приглашение всегда подтверждает ученик; регистрация только связывает его с email.
6. Календарь запрашивает диапазон `[from,to)`, сохраняет `nextCursor` и не разбирает курсор.
7. Для создания урока frontend отправляет `startAt + durationMinutes` либо `startAt + endAt`.

## Регистрация

`POST /auth/register` принимает все данные аккаунта одним запросом:

```ts
type RegisterRequest = {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  roles: Array<"TEACHER" | "STUDENT">;
};
```

Все поля обязательны. В `roles` должна быть минимум одна уникальная роль. У одного пользователя могут быть обе роли. Успешный ответ имеет статус `202`; frontend не считает пользователя вошедшим и показывает экран ожидания письма. После `POST /auth/email-verification/confirm` сервер возвращает `TokenResponse`, устанавливает refresh cookie, а frontend запрашивает `GET /me`.

## JWT-аутентификация

```ts
type TokenResponse = {
  accessToken: string;
  tokenType: "Bearer";
  expiresInSeconds: number;
};
```

`accessToken` добавляется к каждому защищённому запросу:

```http
Authorization: Bearer <accessToken>
```

Refresh token не присутствует в JSON и недоступен JavaScript. Браузер получает его через `Set-Cookie` после login или подтверждения email и отправляет автоматически на `POST /auth/refresh` и `POST /auth/logout`.

Frontend не должен запускать несколько параллельных refresh-запросов после одновременных `401`. Следует использовать один общий refresh promise, затем повторить ожидающие запросы не более одного раза.

После logout frontend удаляет access token из памяти независимо от результата очистки интерфейса. Сервер отзывает refresh token и удаляет cookie.

## Текущий пользователь

```ts
type UserResponse = {
  id: string;
  email: string;
  pendingEmail: string | null;
  firstName: string;
  lastName: string;
  roles: Array<"TEACHER" | "STUDENT">;
  status: "PENDING_EMAIL_VERIFICATION" | "ACTIVE";
  emailVerifiedAt: string | null;
  createdAt: string;
  updatedAt: string;
};
```

В ответах нет исходного пароля и `passwordHash`. Возраст также не является полем пользователя; для ученика он вычисляется из `studentProfile.birthDate`.

`PATCH /me` принимает хотя бы одно из полей `firstName`, `lastName`, `email`. После запроса смены адреса старый адрес остаётся в `email`, а новый приходит в `pendingEmail`. Интерфейс должен показывать состояние ожидания подтверждения, пока `pendingEmail !== null`.

## Профили и роли

`MeResponse` всегда содержит `user`, а `teacherProfile` и `studentProfile` могут быть `null`. Роль сообщает, какой профиль разрешено создать; наличие профиля сообщает, завершён ли соответствующий онбординг. Профиль не владеет email, именем, фамилией, ролями или паролем. Вложенный `user` в ответах профиля — готовая композиция данных для интерфейса.

## Видимость урока

| Поле | Репетитор | Ученик |
|---|---:|---:|
| Участники своего урока | Да | Да |
| Email репетитора | Собственный профиль | Да |
| Место или ссылка | Да | Да |
| Причина и комментарий отмены | Да | Да |
| Цена | Да | Нет |
| `cancelledByUserId` | Да | Нет |

Использовать типы из `contracts.ts`; фактическим источником ограничений остаётся `scheduling.openapi.json`.
