# Frontend-контракты v1.0.0

## Подключение

- Base URL: `/api/v1`.
- Все запросы после входа отправляются с cookie: `credentials: "include"`.
- Для POST/PUT/PATCH/DELETE frontend читает cookie `XSRF-TOKEN` и передаёт `X-XSRF-TOKEN`.
- При `401` открыть вход, при `403 EMAIL_NOT_VERIFIED` показать повторную отправку письма.

## Основные потоки

1. Регистрация → экран ожидания письма → подтверждение → `MeResponse` и сессия.
2. `GET /me` определяет роли и необходимость онбординга по значениям профилей `null`.
3. Приглашение всегда подтверждает ученик; регистрация только связывает его с email.
4. Календарь запрашивает диапазон `[from,to)`, сохраняет `nextCursor` и не разбирает курсор.
5. Для создания урока frontend отправляет `startAt + durationMinutes` либо `startAt + endAt`.

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

Все поля обязательны. В `roles` должна быть минимум одна уникальная роль. У одного пользователя могут быть обе роли. Успешный ответ имеет статус `202`; frontend не считает пользователя вошедшим и показывает экран ожидания письма. После `POST /auth/email-verification/confirm` сервер создаёт сессию и возвращает `MeResponse`.

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
