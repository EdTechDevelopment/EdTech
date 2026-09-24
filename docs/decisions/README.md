# Реестр архитектурных решений и открытых вопросов

Этот документ — рабочая точка входа для разработки backend EdTech и модуля
Identity. Он объединяет обязательные правила backend-архитектуры, Identity-
спецификации и решения, принятые во время реализации `feature/identity`.

Подробные классы, интерфейсы, зависимости и таблицы остаются в
[`docs/architecture`](../architecture/README.md). Этот реестр показывает статус
решений и не позволяет спорным вопросам потеряться в переписке.

## Статусы

| Статус | Значение |
|---|---|
| `FIXED` | Обязательное ограничение архитектуры или публичного контракта. |
| `ACCEPTED` | Решение явно принято для текущей реализации. |
| `TEMPORARY` | Допустимо только на текущем локальном этапе и требует замены. |
| `PLANNED` | Подход определён, но ещё не реализован. |
| `OPEN` | Решение не принято; нельзя угадывать его молча. |
| `SUPERSEDED` | Старый вариант заменён более новым решением. |

## Приоритет источников истины

При расхождении документов применяется следующий порядок:

1. `docs/api/scheduling.openapi.json` — URL, HTTP status, JSON, публичные enum и
   ошибки.
2. `docs/api/api-contracts.md` и `docs/api/frontend-contracts.md` — смысл
   публичного API и правила frontend-интеграции.
3. Общие backend-правила, зафиксированные в этом реестре.
4. Текстовая архитектура Identity в `docs/architecture`.
5. `docs/architecture/architecture.vpp`, сгенерированные HTML, изображения и
   прочий UML-экспорт.

Если публичный контракт меняется, сначала обновляются OpenAPI, примеры и
генерируемые TypeScript DTO, затем backend и архитектурная документация. Два
параллельных варианта одного endpoint, DTO или enum не создаются.

## Зафиксированный технический фундамент

| Решение | Статус | Правило |
|---|---|---|
| Runtime | `ACCEPTED` | Java 21. |
| Build | `ACCEPTED` | Gradle Wrapper 9.7.1, Kotlin DSL. |
| Base package и Gradle group | `ACCEPTED` | `io.github.edtechdevelopment`. |
| Application framework | `ACCEPTED` | Spring Boot 4.1.1. |
| Модульность | `ACCEPTED` | Spring Modulith 2.1.1. |
| HTTP | `FIXED` | Spring Web MVC, base URL `/api/v1`. |
| JSON и validation | `FIXED` | Jackson и Jakarta Bean Validation; бизнес-инварианты остаются в domain. |
| Security | `FIXED` | Spring Security OAuth2 Resource Server и JOSE, stateless authentication. |
| Persistence | `FIXED` | PostgreSQL, jOOQ и `DSLContext`; JPA/Hibernate запрещены. |
| Schema migrations | `FIXED` | Flyway — единственный источник physical schema. |
| Observability | `PLANNED` | Spring Boot Actuator и Micrometer. |
| CI | `PLANNED` | GitHub Actions. |
| Production code | `FIXED` | На старте только Java; второй JVM-язык требует отдельного решения. |

Версии транзитивных библиотек управляются Spring Boot dependency management.
Второй framework с той же ролью не добавляется без ADR.

## Стиль backend и границы модулей

Статус всех правил раздела — `FIXED`.

- Backend является сложным модульным монолитом в одном Spring Boot-приложении.
- Бизнес-модули: `identity`, `tutoring`, `scheduling`, `workflows`,
  `notifications`.
- На текущем этапе это пакеты одного Gradle project, а не отдельные Gradle
  subprojects.
- Каждый бизнес-модуль имеет `api`, `presentation`, `application`, `domain` и
  `infrastructure`.
- У каждого верхнеуровневого модуля есть `package-info.java` для Spring
  Modulith; публичные подпакеты оформляются named interface.
- Другой модуль импортирует только `<module>.api` и не обращается к внутренним
  классам, repositories или таблицам владельца.
- Межмодульная ссылка по UUID не передаёт владение агрегатом и не создаёт
  объектный граф между модулями.
- Синхронный публичный Java API используется, когда ответ нужен для продолжения
  use case. Integration event используется для реакции на уже совершившийся
  факт и обрабатывается после commit.
- Двусторонние синхронные зависимости запрещены; межмодульную координацию
  выполняет `workflows`.

Разрешённые направления:

```text
presentation   → application.port.in / command / query / result
application    → domain / <module>.api
infrastructure → application.port.out / application.model / domain
other module   → <module>.api
```

Запрещённые направления:

```text
domain         -X-> Spring / jOOQ / application / infrastructure
application    -X-> presentation / infrastructure
presentation   -X-> infrastructure / jOOQ repositories
api            -X-> internal packages
module A       -X-> internal packages or tables of module B
```

## Владение данными

| Модуль | Владеет | Не владеет |
|---|---|---|
| Identity | аккаунт, email, password hash, роли, status, email verification, access/refresh tokens | TeacherProfile, StudentProfile, уроки, SMTP |
| Tutoring | профили, предметы, приглашения и связи teacher–student | аккаунт Identity и уроки |
| Scheduling | уроки, участники, интервалы, пересечения и статусы | аккаунт и связь teacher–student |
| Workflows | межмодульная координация | обычная логика одного модуля |
| Notifications | очередь доставки, SMTP, templates, retries и delivery status | решение о том, когда нужно письмо |

Составной HTTP-ответ не меняет владение. Например, `/api/v1/me` может объединять
аккаунт Identity и профили Tutoring, но Identity не переносит к себе профильные
данные.

## Identity: фиксированная граница

Статус — `FIXED`.

- Identity реализуется внутри зафиксированной package/layer архитектуры.
- `identity.api` — единственный публичный Java API модуля.
- `User` и `EmailVerification` — отдельные aggregate roots.
- `EmailVerification` ссылается на пользователя только через UUID; их совместное
  изменение координирует application service.
- `service.account`, `service.verification` и `service.authentication` не
  вызывают друг друга. Общая логика находится в domain, mapper или output port.
- Controller занимается HTTP, validation, principal, cookie и mapping, но не
  обращается к repository и не реализует бизнес-правила.
- Application service задаёт use case и транзакционную границу.
- Domain остаётся чистым Java без Spring, Jackson, jOOQ и persistence annotations.
- Infrastructure реализует output ports; каждый production port имеет одну
  production-реализацию.
- jOOQ generated types и persistence data carriers не выходят из
  `infrastructure.persistence` и не редактируются вручную.

## Канонические Identity-контракты

Статус — `FIXED`, источником является OpenAPI.

```text
Roles: TEACHER, STUDENT
Internal statuses:
  PENDING_EMAIL_VERIFICATION, ACTIVE, SUSPENDED, DEACTIVATED

POST /api/v1/auth/register
POST /api/v1/auth/email-verification/confirm
POST /api/v1/auth/email-verification/resend
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
GET  /api/v1/me
PATCH /api/v1/me
```

- Регистрация требует email, пароль, имя, фамилию и 1–2 уникальные роли.
- Presentation принимает роли регистрации как JSON-массив/`List` и явно
  отклоняет дубли до преобразования в application `Set`; неизвестные JSON-поля
  отклоняются согласно OpenAPI `additionalProperties: false`.
- Email имеет максимум 254 символа; пароль регистрации — 8–72 печатных
  ASCII-символов от `!` до `~` без пробелов; имя и фамилия — 1–100 символов
  после trim. Ограничение в 72 символа одновременно является ограничением в
  72 байта для разрешённого ASCII-набора и не позволяет BCrypt молча отбросить
  хвост пароля.
- Verification token действует 5 минут; значение задаётся configuration property
  `identity.token.verification-ttl`, а не domain-константой.
- Verification token содержит 32 байта криптографической случайности и кодируется
  URL-safe Base64 без padding; размер задаётся свойством
  `identity.token.verification-entropy-bytes`.
- Пока frontend не подключён, `identity.notification.frontend-base-url` использует
  явно временный, синтаксически корректный placeholder
  `http://frontend.example:3000`, а путь подтверждения — `/verify-email`. Реальный
  frontend URL заменяется конфигурацией без изменения кода.
- Регистрация создаёт `PENDING_EMAIL_VERIFICATION`, но не выдаёт access/refresh.
- `POST /api/v1/auth/register` реализован через Presentation → Application →
  Domain/Infrastructure и отвечает `202`.
- `POST /api/v1/auth/email-verification/confirm` реализован полностью: атомарно
  consume verification, активирует/обновляет User, создаёт refresh family,
  возвращает access JWT и устанавливает refresh cookie.
- `POST /api/v1/auth/login` реализован полностью: принимает нормализованный
  `CURRENT` email и пароль, возвращает access JWT, создаёт независимую refresh
  family и устанавливает refresh cookie.
- `POST /api/v1/auth/refresh` реализован полностью: читает token только из
  `HttpOnly` cookie, проверяет обязательный разрешённый `Origin`, ротирует token в
  прежней family с прежним `expiresAt`, возвращает новый access JWT и заменяет
  cookie. Logout реализован идемпотентно и отзывает предъявленную family.
- Confirm и login возвращают `TokenResponse` и устанавливают refresh cookie.
- `TokenResponse` содержит только `accessToken`, `tokenType = Bearer` и
  `expiresInSeconds`.
- Внутренний `AuthenticationResult` содержит только `IssuedAccessToken` и
  `IssuedRefreshToken`. Данные пользователя не дублируются: frontend получает их
  отдельным `GET /me`, что соответствует актуальному HTTP-контракту.
- Refresh token, password, password hash и verification token никогда не входят
  в response JSON.
- Access token передаётся как Bearer и хранится frontend в памяти, а не в
  localStorage или доступной JavaScript cookie.
- `PATCH /me` содержит минимум одно из `firstName`, `lastName`, `email`; userId,
  roles, status и system timestamps через него не изменяются.
- Новый email находится в `pendingEmail`; старый остаётся current до confirm.
- Login по неизвестному email и неверному паролю неразличим для клиента.
- Resend реализован для обоих purpose: `REGISTRATION` по CURRENT email
  неподтверждённого аккаунта и `EMAIL_CHANGE` по PENDING email активного
  аккаунта. Старый активный token purpose инвалидируется; неизвестные и
  неподходящие адреса получают тот же `202` без письма. Endpoint подлежит
  будущему rate limit.
- Формат ошибки: `{code, message, fieldErrors, requestId}`; каждый field error
  содержит `{field, code, message}`.
- Внешнее время — RFC 3339 с offset, ответы формируются в UTC; внутреннее время —
  `Instant`.
- Деньги передаются decimal-строкой и не моделируются через `double`.
- Пагинация использует opaque cursor и `limit`: default 50, maximum 100.

## Domain-инварианты Identity

Статус — `FIXED`.

- Email нормализуется через trim + lowercase и сравнивается по нормализованному
  значению.
- `email`, `passwordHash`, непустые имя/фамилия и минимум одна роль обязательны.
- Один пользователь может иметь `TEACHER`, `STUDENT` или обе роли.
- Новый User всегда имеет `PENDING_EMAIL_VERIFICATION`.
- Активация возможна только после подтверждения registration email.
- Registration email можно подтвердить только для User в состоянии
  `PENDING_EMAIL_VERIFICATION`, и подтверждаемый адрес должен совпадать с current
  email пользователя.
- Current и pending email не совпадают; pending не заменяет current до confirm.
- Смена email не отменяет подтверждённость старого адреса до завершения процесса.
- Запрос и подтверждение смены email разрешены только для `ACTIVE` User. Повторный
  запрос уже установленного pending email выполняется через resend verification,
  а не через повторную смену email.
- Изменение имени и фамилии разрешено только для `ACTIVE` User. `null` означает,
  что соответствующее поле не изменяется; если после нормализации значения
  совпадают с текущими, `updatedAt` не меняется и domain event не создаётся.
- `SUSPENDED` и `DEACTIVATED` запрещают login и refresh.
- Каждое доменное изменение обновляет `updatedAt` и при необходимости создаёт
  domain event.
- Verification нельзя использовать после consume, invalidation или expiry.
- `REGISTRATION` подтверждает current email и активирует User;
  `EMAIL_CHANGE` подтверждает pending email и заменяет current.
- Domain/application получают время через `TimeProvider`/`Clock`, а не вызывают
  `Instant.now()` статически.

## Принятая physical schema Identity

Статус — `ACCEPTED`. Актуальный SQL находится в
`backend/src/main/resources/db/migration`.

- Пока используется стандартная PostgreSQL schema `public` и префикс таблиц
  `identity_`; отдельная PostgreSQL schema для модуля не создаётся.
- Таблицы: `identity_users`, `identity_user_emails`, `identity_user_roles`,
  `identity_email_verifications`, `identity_refresh_tokens`.
- Все timestamps хранятся как `timestamptz` и отображаются в Java как `Instant`.
- `PRIMARY KEY (user_id, kind)` разрешает максимум один `CURRENT` и один
  `PENDING` email пользователя.
- При регистрации неподтверждённый основной email уже имеет kind `CURRENT`;
  подтверждённость выражают status и `email_verified_at`.
- `UNIQUE(email)` резервирует и current, и pending email глобально и остаётся
  последней защитой от race condition.
- `findByEmail` для login ищет только `CURRENT`, а `existsByEmail` проверяет
  `CURRENT` и `PENDING`.
- `PRIMARY KEY (user_id, role)` исключает дубли ролей.
- Raw password и raw token никогда не сохраняются.
- В БД хранятся SHA-256 hashes refresh/verification tokens в каноническом
  lowercase hex-формате из 64 символов; token hashes unique.
- `ON DELETE CASCADE` сохраняется для дочерних Identity-данных. Он срабатывает
  только при физическом DELETE пользователя; обычная деактивация меняет status.

Оставлены только архитектурно значимые `CHECK`:

```text
User status
Email kind
Lowercase email
User role
Verification purpose
expires_at > created_at для обоих видов token
```

Проверки непустых имён, password hash и порядка вспомогательных timestamps
выполняются domain/application и не дублируются отдельными SQL CHECK.

Принятые индексы:

```text
identity_users:
  PRIMARY KEY (id)

identity_user_emails:
  PRIMARY KEY (user_id, kind)
  UNIQUE (email)

identity_user_roles:
  PRIMARY KEY (user_id, role)

identity_email_verifications:
  PRIMARY KEY (id)
  UNIQUE (token_hash)
  INDEX (user_id, purpose)
  INDEX (expires_at)

identity_refresh_tokens:
  PRIMARY KEY (id)
  UNIQUE (token_hash)
  INDEX (user_id, family_id)
  INDEX (expires_at)
```

Составной индекс `(user_id, family_id)` заменяет два отдельных индекса. Он
обслуживает запросы по `user_id` и по паре `user_id + family_id`; операция только
по `family_id` не является поддерживаемым контрактом. Индексы `expires_at`
сохраняются для фоновой очистки.

## Аутентификация и token security

Статус — `FIXED`, кроме явно отмеченных `OPEN` параметров.

- Пароли хешируются BCrypt со strength `10`, заданным свойством
  `identity.security.password.bcrypt-strength`; raw password живёт только во
  время register/login.
- Login всегда выполняет одну BCrypt-проверку. Для неизвестного email используется
  фиксированный фиктивный BCrypt hash; неизвестный email и неверный пароль дают
  одинаковый `401 INVALID_CREDENTIALS`, чтобы не раскрывать наличие аккаунта ни
  телом ответа, ни очевидной разницей времени выполнения.
- Статус аккаунта проверяется только после правильного пароля. Для
  `PENDING_EMAIL_VERIFICATION` login возвращает `403 EMAIL_NOT_VERIFIED`, а для
  `SUSPENDED/DEACTIVATED` — общий `403 FORBIDDEN` без раскрытия деталей состояния.
- Login не блокирует строку `User`: use case не изменяет агрегат, а создаёт только
  новую refresh-family. Уже выпущенный stateless access JWT при последующей смене
  статуса всё равно действует до `exp`, не более 15 минут.
- Access token — короткоживущий JWT RS256 с claims `sub`, `roles`, `iss`, `aud`,
  `iat`, `exp`.
- Access token TTL — 15 минут; `iss = edtech-backend`, `aud = edtech-api`.
- RSA private/public keys загружаются из внешних PEM-файлов и не хранятся в
  репозитории или `application.yml`. `JwtConfiguration` получает пути через
  Spring `@Value`, а встроенный Spring Security converter читает PEM и создаёт
  `RSAPrivateKey`/`RSAPublicKey`; отдельный application loader не используется.
- Для single-instance MVP входящий access JWT проверяется локально по RSA public
  key, фиксированному алгоритму RS256 и `exp` с clock skew `0`, без SQL-запроса
  на каждый request. Подписанные `iss` и `aud` сохраняются в token, но пока не
  участвуют в решении о допустимости: используется отдельная RSA-пара только
  этого backend API. При разделении issuer/resource servers их проверка станет
  обязательной.
- `sub` и `roles` не проверяются отдельным набором JWT validators.
  `IdentityJwtAuthenticationConverter` один раз преобразует `sub` в UUID, а
  известные роли — в `ROLE_*`; невозможность преобразования означает
  недействительную Bearer-аутентификацию (`401`). Domain `User` при этом не
  создаётся и из БД не загружается.
- Refresh token — криптографически случайное opaque URL-safe значение из 32
  random bytes в Base64URL без padding. В БД хранится только lowercase SHA-256
  hex hash.
- Lifetime refresh-token family — фиксированные 30 дней от login; rotation не
  сдвигает общую дату истечения family.
- Refresh cookie: `HttpOnly`, `SameSite=Lax`, path `/api/v1/auth`, `Secure` в
  production, Max-Age равен фактическому оставшемуся lifetime refresh token.
  Локально `identity.security.refresh-cookie.secure=false` из-за HTTP; production
  обязан передать `IDENTITY_REFRESH_COOKIE_SECURE=true` и использовать HTTPS.
- Refresh/logout endpoints защищаются согласованными SameSite, CORS и строгой
  Origin-проверкой; для POST на эти endpoints `Origin` обязателен и должен точно
  входить в configured frontend origins. Запрещённый Origin отклоняется до чтения
  cookie и не очищает её, чтобы внешний сайт не мог инициировать logout через
  ошибочный запрос.
- Каждый login создаёт новую `familyId`; rotation создаёт новый token в той же
  family и отзывает предыдущий.
- Повторное предъявление отозванного refresh token отзывает активные tokens этой
  family в границах пользователя.
- Logout идемпотентно отзывает всю family предъявленного refresh token и очищает
  cookie. Отсутствующий или неизвестный token также приводит к `204`; другие
  login-family пользователя не затрагиваются.
- Старые отозванные refresh rows нельзя удалять немедленно: до retention boundary
  они нужны для reuse detection.
- Raw passwords, JWT, cookies, raw tokens, token hashes и private keys не
  логируются.

### Принятый persistence-контракт refresh

Статус — `ACCEPTED`.

```java
public interface RefreshTokenRepository {
    Optional<RefreshTokenState> findByTokenHash(String tokenHash);
    Optional<RefreshTokenState> findByTokenHashForUpdate(String tokenHash);

    void save(RefreshTokenState token);
    void revoke(UUID tokenId, Instant revokedAt);
    void revokeFamily(UUID userId, UUID familyId, Instant revokedAt);
}
```

Оба lookup ищут запись независимо от expiry/revocation. Неблокирующий
`findByTokenHash` нужен только для предварительного определения `userId`.
Refresh затем блокирует `User`, повторно читает token через
`findByTokenHashForUpdate` (`SELECT ... FOR UPDATE`) и заново сверяет token ID и
owner. Единый порядок `User → RefreshToken` предотвращает цикл с будущей
деактивацией, которая сначала блокирует User, а затем отзывает его sessions.
`RefreshTokenService` различает unknown, expired, revoked и active состояния;
revoked row сохраняет `userId/familyId` для reuse detection.

## Транзакции, конкурентность и messaging

Статус — `FIXED`.

- Каждый mutating use case одного модуля имеет одну `@Transactional` границу на
  application service.
- Register атомарно сохраняет User, CURRENT email, roles и verification.
- Request email change атомарно резервирует PENDING email, сохраняет User и
  verification.
- Confirm атомарно блокирует/consume verification, меняет User и сохраняет refresh
  hash; один verification token нельзя consume дважды.
- Refresh атомарно блокирует `User → RefreshToken`, проверяет повторно прочитанное
  состояние, отзывает старый и сохраняет новый token той же family. Для ожидаемых
  отказов reuse/non-active используется `noRollbackFor`: намеренный отзыв family
  фиксируется вместе с ответом `401/403`; любая неожиданная ошибка по-прежнему
  откатывает транзакцию.
- Database constraints являются последней защитой при конкурентной регистрации и
  смене email; unique violation преобразуется в application exception.
- SMTP не вызывается внутри транзакции Identity. Identity использует только
  `notifications.api`, а Notifications сначала сохраняет запрос доставки.
- `notifications.api` объявлен публичным Spring Modulith named interface. Identity
  передаёт готовый confirmation URL, публичный purpose и точный `expiresAt`
  исходного token; Notifications не обращается к внутренней модели Identity.
- Raw verification token не сохраняется в таблицах Identity. Согласованный MVP
  допускает его кратковременное присутствие внутри confirmation URL в защищённом
  delivery payload Notifications до успешной отправки или истечения срока. Payload
  не логируется и после этой границы удаляется либо затирается.
- Durable delivery request хранится в `notification_email_deliveries` без foreign
  key на Identity. Новое задание создаётся как `PENDING` в той же внешней
  транзакции, что и вызывающий mutating use case, через `Propagation.MANDATORY`.
- `RegisterUserService` и `EnqueueVerificationEmailService` регистрируются явными
  module-owned Spring configurations, а не component scanning. Оба получают
  transaction proxy; Notifications gateway реально отклоняет вызов без внешней
  транзакции. Общий UTC `Clock` принадлежит application composition root, а каждый
  модуль адаптирует его через собственный `TimeProvider`.
- Для MVP не хранятся `attempt_count`, `available_at` и `lease_until`. Временная
  ошибка возвращает delivery в `PENDING`; повтор выполняет следующий общий цикл
  worker-а до `expiresAt`. Зависший `PROCESSING` в будущем определяется по
  `updated_at` и конфигурируемому processing timeout.
- Публичное integration event содержит минимальный JDK-only payload и `eventId`.
  `ApplicationEventPublisher` получает его внутри транзакционного use case, а
  consumer, которому нужны committed данные, обрабатывает событие после commit и
  остаётся идемпотентным.
- `IntegrationEventPublisher` предоставляет отдельную типизированную перегрузку для
  каждого публичного события Identity вместо общего `publish(Object)`. Это не даёт
  application-коду случайно опубликовать domain-объект или произвольное значение.
- `SpringIntegrationEventPublisher` без преобразований передаёт публичный Java
  event в `ApplicationEventPublisher`. Такая публикация сама по себе синхронна и
  хранится только в памяти процесса. Consumer, которому нужны committed данные,
  использует `@TransactionalEventListener(AFTER_COMMIT)`; асинхронность и durable
  delivery этим не подразумеваются.

## Persistence implementation

Статус — `FIXED`/`PLANNED`.

- Flyway migration создаётся раньше изменения jOOQ-модели.
- jOOQ Codegen запускается после применения всех migration к generation database.
- Официальный Gradle-плагин jOOQ Codegen использует ту же версию jOOQ, что и
  runtime. Execution `identity` ограничен таблицами `public.identity_*`, execution
  `notifications` — таблицами `public.notification_*`; оба пишут в один
  `backend/src/generated/java`, но в разные module-owned packages.
- Generated-код сохраняется в `backend/src/generated/java`, фиксируется в Git и
  входит в основной Java source set. `compileJava` намеренно не зависит от
  `jooqCodegen`, поэтому обычная сборка не требует доступной generation database.
- Codegen создаёт table, record, schema, key и index types; POJO и DAO отключены.
- Низкоуровневый repository работает с `DSLContext`, generated records и
  внутренними data carriers и не импортирует domain.
- Adapter реализует application port, вызывает low-level repository, использует
  mapper и переводит database violations в application exceptions.
- `UserJooqRepository` читает три пользовательские таблицы отдельными запросами,
  чтобы не создавать декартово размножение email и roles. При сохранении он
  синхронизирует только исчезнувшие/актуальные email kinds и роли; `created_at`
  после INSERT не обновляется.
- Read-only сценарии используют `findById`; изменение существующего пользователя
  начинается с `findByIdForUpdate`, который блокирует головную строку до чтения
  email и roles. Все writer-ы сначала затрагивают ту же головную строку. Locking
  read и save имеют `Propagation.MANDATORY`, а транзакцию открывает application service.
- Активная email verification ищется по token hash через `SELECT ... FOR UPDATE`.
  Locking read, save и массовая инвалидизация требуют внешнюю транзакцию. Save
  обновляет только `consumed_at`/`invalidated_at`; identity-поля записи неизменяемы.
- PostgreSQL `timestamptz(6)` хранит микросекунды. Production `TimeProvider`
  заранее обрезает более точный `Instant` до микросекунд, поэтому один timestamp
  одинаков в application result, HTTP JSON и сохранённых строках.
- Domain enums преобразует mapper; generated records не получают их напрямую.
- PostgreSQL integration tests не заменяются H2.

## Конфигурация и окружения

- На этапе разработки MVP используется один `application.yml`; отдельный Spring
  profile для локальной среды не применяется.
- Текущие параметры подключения к локальной PostgreSQL в `application.yml`
  являются временными локальными значениями, а не production-конфигурацией.
- Environment variables и command-line properties могут переопределять YAML.
- Локальная RSA-2048 пара находится в ignored-каталоге
  `backend/.local/keys`; `application.yml` содержит только пути к PKCS#8 private
  key и X.509 public key. Между перезапусками локальные ключи сохраняются.
- После clone локальная пара создаётся из `backend` командами:

  ```bash
  mkdir -p .local/keys
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out .local/keys/identity-private.pem
  openssl pkey -in .local/keys/identity-private.pem -pubout -out .local/keys/identity-public.pem
  chmod 600 .local/keys/identity-private.pem
  ```

  Содержимое private key не копируется в конфигурацию и не выводится в логи.
- Production secrets, RSA private key и SMTP credentials поступают извне и не
  хранятся в Git.
- Production profile обязан включать secure cookies и запрещать небезопасные
  local defaults.
- Configuration properties типизированы и валидируются при старте.

Текущая локальная среда имеет статус `TEMPORARY`:

```text
PostgreSQL image: postgres:18.6-alpine
container: edtech-postgres
database/user/password: edtech/edtech/edtech
host port: 5432
Spring configuration: application.yml без отдельного local profile
Mailpit image/container: axllent/mailpit:v1.31.1 / edtech-mailpit
Mailpit SMTP/Web ports: 1025 / 8025
local sender address: no-reply@edtech.local
```

Эти credentials, адрес отправителя и публикация портов `5432`, `1025`, `8025`
допустимы только для локальной разработки и не переносятся в production.
Mailpit является локальным SMTP-catcher и не отправляет письма в реальные
почтовые системы. В production тот же Spring Mail adapter подключается к
внешнему SMTP-провайдеру; host, port, username, password, TLS/auth flags и
настоящий `from`-адрес поступают из deployment configuration/secret storage и
не хранятся в репозитории.

## Статус реализации Identity для MVP

Статус модуля — `PARTIAL / MVP FOUNDATION COMPLETE`.

Завершён самостоятельный Identity-срез:

- регистрация упрощённым account-only запросом;
- подтверждение registration email с немедленной выдачей access/refresh tokens;
- resend для `REGISTRATION` и `EMAIL_CHANGE`;
- login, refresh rotation/reuse detection и logout;
- изменение имени, фамилии и account email через pending verification;
- доставка verification-писем через durable очередь Notifications, Spring Mail
  и локальный Mailpit;
- публичный Java query `IdentityQuery.findUserById(UUID)`;
- Flyway/jOOQ persistence, JWT/security и транзакционные блокировки;
- unit, application, web/security, PostgreSQL integration и Spring Modulith tests.

Модуль не считается полностью завершённым относительно целевой архитектуры.
После появления Tutoring и Workflows необходимо:

- заменить прямую account-only регистрацию на `RegistrationWorkflow`, который
  атомарно создаёт Identity User и обязательные Tutoring profiles;
- добавить принадлежащий Identity `birthDate` в `User`, migration, jOOQ,
  persistence, public API и registration workflow;
- реализовать `IdentityRegistrationCommands` с `operationId` и присоединением к
  внешней workflow-транзакции;
- реализовать добавление второй роли через `RoleOnboardingWorkflow` и
  `AddUserRoleService`, одновременно создавая соответствующий профиль;
- завершить составной `GET /api/v1/me`, `MeResponse`, `MeQueryFacade` и
  `GetCurrentUserUseCase` без временных `null`-профилей;
- расширить `IdentityQuery` пакетным/email-чтением только при появлении реальных
  Tutoring use cases;
- синхронизировать целевые registration/profile/birthDate схемы OpenAPI и
  frontend DTO с этой архитектурой.

До production также остаются открытые `OPEN-005`, `OPEN-009`, `OPEN-011`,
`OPEN-012`, `OPEN-017`, `OPEN-019` и `OPEN-020`.

### Контрольная проверка 2026-09-24

- полный `./gradlew build` завершён успешно;
- выполнено 277 тестов в 65 test suites: `0 failures`, `0 errors`, `0 skipped`;
- успешно собраны `jar` и `bootJar`, выполнены `check` и Spring Modulith
  verification;
- OpenAPI проверен как корректный JSON, staged/working diff не содержит
  whitespace errors;
- после первого полного прогона PostgreSQL-проверка показала отсутствие строк в
  `identity_users`, тестовых `@example.test` email и тестовых notification
  deliveries; DB flow-тесты используют rollback и дополнительно проверяют cleanup.

## Логирование, наблюдаемость и тесты

Статус требований — `PARTIAL`.

- Сейчас каждый `ApiError` получает уникальный ID при формировании ошибки;
  unexpected error логируется с тем же ID.
- Единый request-wide `requestId`/trace ID для всего запроса и всех логов пока не
  реализован и должен появиться вместе с полноценной observability.
- Логируются use case, технический результат, длительность и безопасные ID.
- Actuator публикует наружу только явно разрешённые endpoints.
- Domain unit tests проверяют инварианты без Spring.
- Application tests используют fake/mock output ports.
- Текущие persistence/integration tests используют локальный PostgreSQL из
  Docker Compose, Flyway и реальные SQL, constraints, locking и concurrency.
  Переход на изолированный Testcontainers PostgreSQL остаётся улучшением
  тестовой инфраструктуры, а не выполненным фактом.
- Web/security tests проверяют OpenAPI, cookie, JWT, CORS/Origin и `ApiError`.
- Интеграционный тест регистрации использует явно маркированный адрес
  `registration-flow-<uuid>@example.test`, проверяет HTTP → Identity → Notifications
  и выполняется с автоматическим rollback; после транзакции дополнительно
  проверяется отсутствие тестовых строк.
- Spring Modulith и ArchUnit проверяют module/layer boundaries.

Рабочее соглашение для пошаговой разработки имеет статус `ACCEPTED`:

- реализация ведётся небольшими проверяемыми шагами;
- до изменения объясняются цель, способ и причина; пользователь подтверждает шаг;
- во время изменения сообщается о выполненных подзадачах;
- по умолчанию выполняется только соразмерная smoke-проверка build/start;
- полный integration/concurrency/security прогон выполняется по прямому запросу
  или перед признанием Identity полностью готовым. Это не отменяет критерии
  готовности feature.

## Открытые вопросы

Ни один пункт со статусом `OPEN` нельзя закрывать неявным выбором в коде.

| ID | Статус | Вопрос и влияние |
|---|---|---|
| `OPEN-001` | `RESOLVED` | HTTP/OpenAPI `UserStatus`, public Java `UserStatusView` и внутренняя Identity-модель содержат одинаковые четыре состояния: `PENDING_EMAIL_VERIFICATION`, `ACTIVE`, `SUSPENDED`, `DEACTIVATED`. API не скрывает suspended/deactivated state, если такой User возвращается разрешённым endpoint. |
| `OPEN-002` | `RESOLVED` | MVP использует строгий reuse detection без grace window: повторное предъявление отозванного refresh token отзывает активные tokens его family. Frontend сериализует refresh через один общий promise и не выполняет автоматический retry самого `/auth/refresh`. |
| `OPEN-003` | `RESOLVED` | Verification TTL — 5 минут, access JWT TTL — 15 минут, refresh family lifetime — 30 дней, verification/refresh entropy — 32 байта, BCrypt strength — 10. Для single-instance MVP JWT clock skew равен 0. Значения задаются configuration properties, а не domain constants. |
| `OPEN-004` | `RESOLVED` | MVP не использует sliding session: family имеет фиксированный 30-дневный lifetime, а каждый token, созданный при rotation, наследует исходный `expiresAt` family. Refresh не продлевает общую аутентификацию. |
| `OPEN-005` | `OPEN` | Точный retention period, batch size, расписание и владелец фоновой очистки expired verification/refresh rows ещё не определены. Индексы `expires_at` уже сохранены под этот use case. |
| `OPEN-006` | `RESOLVED` | `email_verified_at` хранит время подтверждения текущего account email. При успешном `EMAIL_CHANGE` значение обновляется; первоначальное время активации при необходимости должно храниться отдельным полем. |
| `OPEN-007` | `RESOLVED` | Resend выбирает `REGISTRATION`, если адрес является CURRENT email пользователя в `PENDING_EMAIL_VERIFICATION`, и `EMAIL_CHANGE`, если адрес является PENDING email. Для подтверждённого CURRENT, неизвестного или неподходящего адреса письмо не создаётся; HTTP-ответ во всех случаях остаётся нейтральным `202`. |
| `OPEN-008` | `RESOLVED` | SHA-256 hashes хранятся в каноническом lowercase hex-формате из 64 символов. |
| `OPEN-009` | `OPEN` | Жизненный цикл hard delete пользователя, сроки хранения и требования аудита/персональных данных не определены. До решения бизнес-деактивация использует `DEACTIVATED`, а `ON DELETE CASCADE` относится только к физическому DELETE. |
| `OPEN-010` | `RESOLVED` | Для MVP `SUSPENDED/DEACTIVATED` запрещают новые login/refresh, но уже выданный stateless access JWT действует до `exp` (не более 15 минут). SQL-проверка account status на каждом endpoint и access-token blacklist не вводятся. Решение пересматривается при появлении критичных операций с требованием мгновенного отзыва. |
| `OPEN-011` | `OPEN` | Формат durable outbox для межмодульных integration events ещё не выбран. Нельзя имитировать требуемую надёжность обычным in-memory event. Notification delivery использует отдельную принадлежащую Notifications durable job queue, а не integration-event outbox. |
| `OPEN-012` | `OPEN` | Production deployment platform, secret storage, tracing backend и metrics storage ещё не выбраны. |
| `OPEN-013` | `RESOLVED` | Для локальной разработки используется `axllent/mailpit:v1.31.1`: SMTP `localhost:1025`, Web UI `localhost:8025`. Mailpit запрещён в production; production использует внешний SMTP-провайдер и секреты окружения. |
| `OPEN-014` | `RESOLVED` | Confirm атомарно находит активную verification через `SELECT ... FOR UPDATE`, изменяет агрегат и сохраняет `consumed_at` в той же внешней транзакции. `Propagation.MANDATORY` не позволяет освободить row lock раньше завершения use case. |
| `OPEN-015` | `RESOLVED` | Для изменения существующего пользователя используется `findByIdForUpdate`: `SELECT ... FOR UPDATE` блокирует строку `identity_users` до чтения email/roles и последующего save. Все writer-ы сначала upsert-ят ту же головную строку. Locking read и save требуют внешнюю транзакцию через `Propagation.MANDATORY`. |
| `OPEN-016` | `RESOLVED` | Все операции, изменяющие `User` и `EmailVerification`, получают locks в порядке `User → EmailVerification`. Confirm сначала неблокирующе читает verification, чтобы узнать `userId`, затем блокирует User, повторно читает verification с `FOR UPDATE` и заново валидирует её состояние. |
| `OPEN-017` | `OPEN` | Для MVP confirmation URL временно хранится в чувствительном Notifications delivery payload и удаляется/затирается после отправки либо `expiresAt`. Перед production нужно решить, требуется ли application-level encryption at rest, и выбрать точный retention delivery metadata, включая срок хранения `recipient_email` как персональных данных. |
| `OPEN-018` | `RESOLVED` | MVP worker использует `batch-size=10`, `poll-delay=10s`, `initial-delay=10s`, `processing-timeout=1m`, один scheduler и повторную попытку на общем цикле без `attempt_count`, `available_at`, `lease_until`. Ошибка подготовки письма постоянная; прочие известные Spring Mail transport/auth ошибки временные. Зависший `PROCESSING` восстанавливается по `updated_at`. При текущем малом объёме отдельный claim-индекс не добавляется; решение пересматривается перед несколькими instances или ростом очереди. |
| `OPEN-019` | `OPEN` | HTTP-контракт предусматривает `429`, но rate limiting для login, register и verification endpoints ещё не реализован. Нужно отдельно выбрать лимиты, ключи ограничения (IP/account/device), хранилище счётчиков и поведение за reverse proxy. |
| `OPEN-020` | `OPEN` | Resend или замена `pendingEmail` через `PATCH /me` инвалидирует прежний verification token, но уже сохранённая `PENDING` delivery Notifications пока не отменяется. Worker может отправить старое письмо с уже недействительной ссылкой перед новым. Безопасность сохраняется, однако до production нужно выбрать deduplication/cancellation contract Notifications или закрыть окно согласованным rate limit. |
| `OPEN-021` | `RESOLVED` | До реализации Tutoring не создаётся временный HTTP `GET /me` с одним `UserResponse` или `null`-профилями. Identity предоставляет межмодульный `IdentityQuery.findUserById(UUID) → Optional<UserSummary>`. Полный `GET /me`, `MeResponse`, `MeQueryFacade` и `GetCurrentUserUseCase` завершаются во время разработки Tutoring. |
| `OPEN-022` | `PLANNED` | Текущий `ApiError.requestId` создаётся только при формировании ошибки. Полноценный request-wide correlation/trace ID, пробрасываемый через весь запрос, ответы и логи, будет реализован вместе с observability; документация не должна описывать его как уже работающий. |

## Известный долг документации

- Сгенерированный HTML, изображения и `architecture.vpp` могут содержать старые
  `TUTOR`, `ADMIN`, `PENDING_VERIFICATION`, endpoint paths и прежние refresh-port
  signatures.
- Канонические Markdown-каталоги исправляются сразу; Visual Paradigm source и
  generated export должны быть синхронизированы отдельным экспортом.
- Файлы исходных спецификаций, переданные вне репозитория, не изменяются
  автоматически. Принятые отклонения от них фиксируются в этом реестре.
