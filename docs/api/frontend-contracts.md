# Frontend-контракты v1.2.0

> Статус: HTTP Tutoring и Workflows MVP реализованы. Точные схемы — [tutoring.openapi.json](tutoring.openapi.json) и [workflows.openapi.json](workflows.openapi.json). Регистрация с профилями, GET /me и onboarding работают; unlink и статистика уроков отложены до Scheduling.

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
6. Изменение контактного email профиля сохраняется сразу после проверки формата и не изменяет account email.
7. Приглашение всегда принимает ученик; регистрация только связывает его с подтверждённым account email.
8. Календарь запрашивает диапазон `[from,to)`, сохраняет `nextCursor` и не разбирает курсор.
9. Поиск активных профилей доступен без входа; предметы и возраст берутся из публичных профильных данных.

GET /me и MeResponse реализованы через MeQueryFacade. Actor определяется по Bearer principal; произвольный userId в запросе не выбирает другого владельца. IdentityQuery/OwnAccountQuery — внутренние межмодульные Java-контракты, не HTTP-ручки frontend.

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

`contactEmail` может совпадать с account `email` или содержать другой адрес. Это контактные данные профиля; адрес не подтверждается и не изменяется обработчиком `AccountEmailVerifiedEvent`.

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

Профиль хранит собственные `displayName`, `contactEmail`, `contactDetails` и выбранные предметы. Он не хранит дату рождения или возраст: в self/public-ответах возраст вычисляется Identity. В `MeResponse` данные аккаунта, включая точную `birthDate`, находятся в `user`, а профили — в соседних `teacherProfile`/`studentProfile`; внутри профилей повторного `user` нет.

Существующий профиль обновляется через:

```text
PUT /api/v1/tutoring/profiles/teacher
PUT /api/v1/tutoring/profiles/student
```

Эти endpoint не создают профиль; `contactEmail` обновляется вместе с остальными полями.

Вторая роль вместе с первоначальным профилем создаётся атомарно через:

```text
POST /me/roles/teacher
POST /me/roles/student
```

После ответа `201 MeResponse` frontend вызывает `POST /auth/refresh`, чтобы получить JWT с новым набором ролей.
Оба запроса на добавление роли требуют стабильный `Idempotency-Key: UUID`.

## Контактный email профиля

Контактный адрес входит в обычный запрос обновления своего профиля:

```text
PUT /api/v1/tutoring/profiles/teacher
PUT /api/v1/tutoring/profiles/student
```

Полное тело запроса профиля содержит обязательный `contactEmail` вместе с остальными полями. После проверки формата адрес сохраняется сразу; ответ `204`. Письмо с подтверждением не отправляется. Этот адрес может отличаться от account email.

## Публичные профили, контакты и возраст

- `GET /api/v1/tutoring/profiles/teachers` и `/students` дают публичный поиск активных профилей с фильтрами `subjectCode`, `minAge`, `maxAge`, `cursor`; сервер возвращает до 20 коротких карточек и `nextCursor`. Размер страницы клиент не задаёт. Выбранные предметы берутся из профиля.
- Элементы поиска — `TeacherProfileCardView` и `StudentProfileCardView`. Карточка репетитора содержит `userId`, `age`, `displayName`, фото, коды предметов, город и фрагмент описания до 200 символов; карточка ученика — `userId`, `age`, `displayName`, фото и коды предметов. Названия предметов клиент берёт из справочника.
- Нажатие на карточку открывает `GET /api/v1/tutoring/profiles/teachers/{userId}` или `/students/{userId}`. Этот запрос возвращает полный профиль активного пользователя без login и без связи: `contactDetails`, контактный email и остальные поля соответствующего профиля. Точная дата рождения остаётся в данных аккаунта и видна только владельцу.
- Указанный `contactEmail` виден всем посетителям активного профиля без подтверждения владения адресом. Account email не подставляется вместо профильного.
- Учебные списки и уроки используют профильный `displayName`, а не `Identity.firstName + lastName`. Список связей и статистика уроков требуют отдельного права доступа; публичность профиля их не открывает.
- `GET /api/v1/tutoring/subjects` возвращает массив `{subjectCode,name}` без login. Справочник помогает выбрать допустимый код; предметы конкретного человека читаются из его профиля.

## Видимость урока

| Поле | Преподаватель | Ученик |
|---|---:|---:|
| Участники своего урока | Да | Да |
| Контактный email преподавателя | Собственный профиль | Да |
| Место или ссылка | Да | Да |
| Причина и комментарий отмены | Да | Да |
| Цена | Да | Нет |
| `cancelledByUserId` | Да | Нет |

Использовать типы из `contracts.ts` и сверять их с `scheduling.openapi.json` версии `1.2.0`.


## Правила реализованного HTTP Tutoring

photoUrl принимает абсолютную ссылку http/https с хостом. null или отсутствие этого поля PUT удаляет фото; пустая строка ошибочна. PUT заменяет все обычные поля; обязательные коллекции передаются даже пустыми (для teacher предметы непустые). Контактный адрес профиля сохраняется сразу без email verification.

Публичный поиск отдаёт 20 карточек. nextCursor передаётся обратно без разбора и с теми же фильтрами; при изменении фильтров поиск начинается заново. Для поиска пользовательский limit не предусмотрен. Списки приглашений/связей используют limit 1…100, default 50.

Ссылка письма /invitations/{id}?email=... предназначена будущему frontend. Email предзаполняет заблокированное поле регистрации; параметр не даёт права принятия. После подтверждения email приглашение появится во входящих. Reject возможен до StudentProfile; accept требует StudentProfile и отдельного действия. Составная backend-регистрация реализована. Страница приглашения и заблокированное поле email ещё требуют frontend.


## Реализованные повторы и onboarding

Один Idempotency-Key: UUID обозначает одну операцию Workflows. Для сетевого повтора используйте тот же ключ и эквивалентные данные; для исправленного/другого запроса создавайте новый. REGISTER нормализует email/имя, порядок ролей и предметов не влияет на fingerprint. Пароль, порядок контактов и различие null/пустого описания значимы. При конфликте ключа ответ 409 IDEMPOTENCY_CONFLICT.

После 201 добавления роли выполните существующий POST /auth/refresh, чтобы заменить access JWT ролями из актуального аккаунта. Затем GET /me возвращает текущие account/profile данные. Повтор onboarding возвращает первоначальный сохранённый 201, даже если обычные профильные поля позже обновлены.

POST регистрации/onboarding ограничены 1 MiB по умолчанию. Кнопка отвязки и карточка со статистикой пока не имеют backend endpoint и включаются вместе со Scheduling.
