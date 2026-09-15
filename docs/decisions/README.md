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
- Email имеет максимум 254 символа; пароль регистрации — 8–128 печатных
  ASCII-символов от `!` до `~` без пробелов; имя и фамилия — 1–100 символов
  после trim.
- Verification token действует 5 минут; значение задаётся configuration property
  `identity.token.verification-ttl`, а не domain-константой.
- Регистрация создаёт `PENDING_EMAIL_VERIFICATION`, но не выдаёт access/refresh.
- Confirm и login возвращают `TokenResponse` и устанавливают refresh cookie.
- `TokenResponse` содержит только `accessToken`, `tokenType = Bearer` и
  `expiresInSeconds`.
- Refresh token, password, password hash и verification token никогда не входят
  в response JSON.
- Access token передаётся как Bearer и хранится frontend в памяти, а не в
  localStorage или доступной JavaScript cookie.
- `PATCH /me` содержит минимум одно из `firstName`, `lastName`, `email`; userId,
  roles, status и system timestamps через него не изменяются.
- Новый email находится в `pendingEmail`; старый остаётся current до confirm.
- Login по неизвестному email и неверному паролю неразличим для клиента.
- Resend не раскрывает существование аккаунта и подлежит rate limit.
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

- Пароли хешируются BCrypt; raw password живёт только во время register/login.
- Access token — короткоживущий JWT RS256 с claims `sub`, `roles`, `iss`, `aud`,
  `iat`, `exp`.
- Входящий access JWT проверяется локально по public key, issuer, audience и
  expiry без SQL-запроса на каждый request.
- Refresh token — криптографически случайное opaque URL-safe значение.
- Refresh cookie: `HttpOnly`, `SameSite=Lax`, path `/api/v1/auth`, `Secure` в
  production, Max-Age равен lifetime refresh token.
- Refresh/logout endpoints защищаются согласованными SameSite, CORS и Origin
  checks; CORS разрешает только configured frontend origins.
- Каждый login создаёт новую `familyId`; rotation создаёт новый token в той же
  family и отзывает предыдущий.
- Повторное предъявление отозванного refresh token отзывает активные tokens этой
  family в границах пользователя.
- Logout идемпотентно отзывает предъявленный refresh token и очищает cookie.
- Старые отозванные refresh rows нельзя удалять немедленно: до retention boundary
  они нужны для reuse detection.
- Raw passwords, JWT, cookies, raw tokens, token hashes и private keys не
  логируются.

### Принятый persistence-контракт refresh

Статус — `ACCEPTED`.

```java
public interface RefreshTokenRepository {
    Optional<RefreshTokenState> findByTokenHashForUpdate(String tokenHash);

    RefreshTokenState save(RefreshTokenState token);
    void revoke(UUID tokenId, Instant revokedAt);
    void revokeFamily(UUID userId, UUID familyId, Instant revokedAt);
}
```

`findByTokenHashForUpdate` ищет запись независимо от expiry/revocation и выполняет
`SELECT ... FOR UPDATE`. Метод не изменяет строку сам: он удерживает row-level lock
до конца transaction. `RefreshTokenService` различает unknown, expired, revoked и
active состояния. Только так revoked token сохраняет доступные `userId/familyId`
для reuse detection.

## Транзакции, конкурентность и messaging

Статус — `FIXED`.

- Каждый mutating use case одного модуля имеет одну `@Transactional` границу на
  application service.
- Register атомарно сохраняет User, CURRENT email, roles и verification.
- Request email change атомарно резервирует PENDING email, сохраняет User и
  verification.
- Confirm атомарно блокирует/consume verification, меняет User и сохраняет refresh
  hash; один verification token нельзя consume дважды.
- Refresh атомарно блокирует старый token, проверяет состояние пользователя,
  отзывает старый и сохраняет новый token той же family.
- Database constraints являются последней защитой при конкурентной регистрации и
  смене email; unique violation преобразуется в application exception.
- SMTP не вызывается внутри транзакции Identity. Identity использует только
  `notifications.api`, а Notifications сначала сохраняет запрос доставки.
- Публичное integration event содержит минимальный JDK-only payload и `eventId`,
  публикуется/обрабатывается после commit и требует идемпотентного consumer.
- `IntegrationEventPublisher` предоставляет отдельную типизированную перегрузку для
  каждого публичного события Identity вместо общего `publish(Object)`. Это не даёт
  application-коду случайно опубликовать domain-объект или произвольное значение.

## Persistence implementation

Статус — `FIXED`/`PLANNED`.

- Flyway migration создаётся раньше изменения jOOQ-модели.
- jOOQ Codegen запускается после применения всех migration к generation database.
- Официальный Gradle-плагин jOOQ Codegen использует ту же версию jOOQ, что и
  runtime. Генерация ограничена таблицами `public.identity_*`.
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
- Domain enums преобразует mapper; generated records не получают их напрямую.
- PostgreSQL integration tests не заменяются H2.

## Конфигурация и окружения

- На этапе разработки MVP используется один `application.yml`; отдельный Spring
  profile для локальной среды не применяется.
- Текущие параметры подключения к локальной PostgreSQL в `application.yml`
  являются временными локальными значениями, а не production-конфигурацией.
- Environment variables и command-line properties могут переопределять YAML.
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
```

Эти credentials и публикация `5432:5432` допустимы только для локальной
разработки и не переносятся в production.

## Логирование, наблюдаемость и тесты

Статус архитектурных требований — `FIXED`.

- Каждый request получает requestId/trace ID; публичная ошибка возвращает
  `requestId`.
- Логируются use case, технический результат, длительность и безопасные ID.
- Actuator публикует наружу только явно разрешённые endpoints.
- Domain unit tests проверяют инварианты без Spring.
- Application tests используют fake/mock output ports.
- Persistence tests используют Testcontainers PostgreSQL, Flyway и реальные SQL,
  constraints, locking и concurrency.
- Web/security tests проверяют OpenAPI, cookie, JWT, CORS/Origin и `ApiError`.
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
| `OPEN-001` | `OPEN` | OpenAPI HTTP `UserStatus` содержит только `PENDING_EMAIL_VERIFICATION` и `ACTIVE`, а внутренняя/Public Java Identity-модель также содержит `SUSPENDED` и `DEACTIVATED`. Нужно решить, расширять ли OpenAPI или гарантировать, что эти состояния никогда не сериализуются в `UserResponse`. |
| `OPEN-002` | `OPEN` | Политика почти одновременных refresh-запросов: строгий reuse detection отзывает family для проигравшего повторного запроса. Frontend обязан сериализовать refresh через один promise, но нужно решить, нужен ли серверный grace window для сетевых повторов. |
| `OPEN-003` | `OPEN` | `verificationTokenTtl` зафиксирован как 5 минут. Точные `accessTokenTtl`, `refreshTokenTtl`, entropy bytes и BCrypt strength ещё не выбраны. Все значения должны быть configuration properties, а не domain constants. |
| `OPEN-004` | `OPEN` | Нужны max absolute lifetime token family и sliding-session policy либо только TTL каждой отдельной refresh-записи. |
| `OPEN-005` | `OPEN` | Точный retention period, batch size, расписание и владелец фоновой очистки expired verification/refresh rows ещё не определены. Индексы `expires_at` уже сохранены под этот use case. |
| `OPEN-006` | `OPEN` | Семантика `email_verified_at`: каталог БД называет его временем первого подтверждения, а API contract говорит обновлять при подтверждённой смене email. Нужно выбрать один смысл и синхронизировать имя/описание. |
| `OPEN-007` | `OPEN` | Resend принимает только email. Нужно формально определить, как он выбирает `REGISTRATION` или `EMAIL_CHANGE`, когда email может быть CURRENT или PENDING, сохраняя нейтральный ответ. |
| `OPEN-008` | `RESOLVED` | SHA-256 hashes хранятся в каноническом lowercase hex-формате из 64 символов. |
| `OPEN-009` | `OPEN` | Жизненный цикл hard delete пользователя, сроки хранения и требования аудита/персональных данных не определены. До решения бизнес-деактивация использует `DEACTIVATED`, а `ON DELETE CASCADE` относится только к физическому DELETE. |
| `OPEN-010` | `OPEN` | При `SUSPENDED/DEACTIVATED` новые login/refresh запрещены, но уже выданный stateless access JWT живёт до expiry. Нужно решить, достаточно ли короткого TTL или критичные endpoints должны дополнительно проверять account status. |
| `OPEN-011` | `OPEN` | Формат durable outbox для межмодульных events/notification delivery ещё не выбран. Нельзя имитировать требуемую надёжность обычным in-memory event. |
| `OPEN-012` | `OPEN` | Production deployment platform, secret storage, tracing backend и metrics storage ещё не выбраны. |
| `OPEN-013` | `OPEN` | Локальный Mailpit и его настройки предусмотрены backend-архитектурой, но пока сознательно не добавлены в Compose. |
| `OPEN-014` | `OPEN` | Confirm обязан атомарно consume verification, но конкретный persistence-механизм ещё не выбран: `SELECT ... FOR UPDATE` с последующим save или conditional update. Публичный use case от выбора не меняется. |
| `OPEN-015` | `RESOLVED` | Для изменения существующего пользователя используется `findByIdForUpdate`: `SELECT ... FOR UPDATE` блокирует строку `identity_users` до чтения email/roles и последующего save. Все writer-ы сначала upsert-ят ту же головную строку. Locking read и save требуют внешнюю транзакцию через `Propagation.MANDATORY`. |

## Известный долг документации

- Сгенерированный HTML, изображения и `architecture.vpp` могут содержать старые
  `TUTOR`, `ADMIN`, `PENDING_VERIFICATION`, endpoint paths и прежние refresh-port
  signatures.
- Канонические Markdown-каталоги исправляются сразу; Visual Paradigm source и
  generated export должны быть синхронизированы отдельным экспортом.
- Файлы исходных спецификаций, переданные вне репозитория, не изменяются
  автоматически. Принятые отклонения от них фиксируются в этом реестре.
