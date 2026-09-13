# Каталог классов и типов

Каталог выполняет роль переносимого Class Repository. Полное имя элемента образуется как `identity.<пакет>.<имя>`. Для DTO и неизменяемых моделей предпочтительны Java `record`; для enum указан набор значений; для сервисов и адаптеров указана их основная ответственность.

## Публичный API

### `identity.api.model`

| Элемент | Стереотип | Поля | Назначение |
|---|---|---|---|
| `UserSummary` | `record` | `UUID id`, `String email`, `String firstName`, `String lastName`, `Set<UserRoleView> roles`, `UserStatusView status` | Безопасное публичное представление пользователя для других модулей. Не раскрывает password hash, pending email и token data. |
| `UserRoleView` | `enum` | `TEACHER`, `STUDENT` | Публичное представление роли. Один пользователь может иметь обе роли. |
| `UserStatusView` | `enum` | `PENDING_EMAIL_VERIFICATION`, `ACTIVE`, `SUSPENDED`, `DEACTIVATED` | Публичное Java-представление состояния учётной записи. Расхождение с HTTP enum отмечено в реестре решений. |

### `identity.api.event`

| Элемент | Стереотип | Поля | Назначение |
|---|---|---|---|
| `UserRegisteredEvent` | `record`, integration event | `UUID eventId`, `UUID userId`, `String email`, `Instant occurredAt` | Сообщает другим модулям, что пользователь зарегистрирован и ожидает подтверждения. |
| `UserActivatedEvent` | `record`, integration event | `UUID eventId`, `UUID userId`, `String email`, `Instant occurredAt` | Сообщает, что email подтверждён и пользователь активирован. |
| `UserAccountUpdatedEvent` | `record`, integration event | `UUID eventId`, `UUID userId`, `Set<String> changedFields`, `Instant occurredAt` | Сообщает об изменении публично значимых данных пользователя. |

Публичные события являются стабильными контрактами. Они создаются из доменных событий или результата use case, но не содержат ссылки на domain-типы.

## Presentation: Auth

### `identity.presentation.auth.model.request`

| Элемент | Стереотип | Поля | Назначение |
|---|---|---|---|
| `RegisterRequest` | `record`, request DTO | `String email`, `String password`, `String firstName`, `String lastName`, `Set<UserRoleView> roles` | JSON-запрос регистрации. Все поля обязательны; roles содержит 1–2 уникальных значения. |
| `LoginRequest` | `record`, request DTO | `String email`, `String password` | JSON-запрос входа. Открытый пароль живёт только в пределах обработки запроса. |
| `ConfirmEmailRequest` | `record`, request DTO | `String token` | Запрос подтверждения email по открытому verification token. |
| `ResendEmailVerificationRequest` | `record`, request DTO | `String email` | Запрос повторного выпуска verification token. Ответ не должен позволять определить существование email. |

### `identity.presentation.auth.model.response`

| Элемент | Стереотип | Поля | Назначение |
|---|---|---|---|
| `TokenResponse` | `record`, response DTO | `String accessToken`, `String tokenType`, `long expiresInSeconds` | Тело успешного login/confirm/refresh. Refresh token отсутствует в JSON и записывается в cookie. |
| `VerificationPendingResponse` | `record`, response DTO | `String email`, `Instant verificationExpiresAt` | Ответ регистрации или повторной отправки подтверждения. Не раскрывает userId. |

### Контроллеры и вспомогательные классы Auth

| Элемент | Пакет | Стереотип | Назначение |
|---|---|---|---|
| `AuthController` | `presentation.auth.controller` | `@RestController` | Обрабатывает регистрацию, login, refresh и logout. Вызывает соответствующие input ports, создаёт/очищает refresh cookie и возвращает HTTP DTO. |
| `EmailVerificationController` | `presentation.auth.controller` | `@RestController` | Обрабатывает confirm и resend. При успешном confirm возвращает access token и устанавливает refresh cookie. |
| `AuthPresentationMapper` | `presentation.auth.mapper` | mapper | Преобразует auth request DTO в application commands, а application results — в response DTO. Не содержит бизнес-правил. |
| `RefreshTokenCookieFactory` | `presentation.auth.cookie` | component | Создаёт и очищает refresh cookie. Централизует `HttpOnly`, `Secure`, `SameSite=Lax`, path `/api/v1/auth` и срок жизни. |

Рекомендуемые HTTP-операции:

```text
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
POST /api/v1/auth/email-verification/confirm
POST /api/v1/auth/email-verification/resend
```

## Presentation: Account

### `identity.presentation.account.model`

| Элемент | Стереотип | Поля | Назначение |
|---|---|---|---|
| `UpdateCurrentUserRequest` | `record`, request DTO | `String email`, `String firstName`, `String lastName` | Частичное изменение профиля. Неизменяемое поле можно передавать как `null`; правило «есть хотя бы одно изменение» проверяет class-level validator. |
| `UserResponse` | `record`, response DTO | `UUID id`, `String email`, `String? pendingEmail`, `String firstName`, `String lastName`, `Set<UserRoleView> roles`, `UserStatusView status`, `Instant? emailVerifiedAt`, `Instant createdAt`, `Instant updatedAt` | REST-представление текущего пользователя. Nullable-поля присутствуют в JSON со значением null. |

### Контроллер, mapper и validation

| Элемент | Пакет | Стереотип | Назначение |
|---|---|---|---|
| `CurrentUserController` | `presentation.account.controller` | `@RestController` | Обрабатывает `GET /api/v1/me` и `PATCH /api/v1/me`; user ID получает из проверенного `Authentication`. |
| `UserPresentationMapper` | `presentation.account.mapper` | mapper | Преобразует `UpdateCurrentUserRequest` в command и `CurrentUserResult` в `UserResponse`. |
| `ValidAccountUpdate` | `presentation.account.validation` | `@Constraint` | Аннотация составной валидации `UpdateCurrentUserRequest`. |
| `AccountUpdateValidator` | `presentation.account.validation` | `ConstraintValidator` | Проверяет, что указан хотя бы один изменяемый атрибут и что сочетание значений допустимо на уровне HTTP-контракта. |

## Presentation: Error

| Элемент | Пакет | Стереотип | Поля / интерфейс | Назначение |
|---|---|---|---|---|
| `ApiError` | `presentation.error.model` | `record`, response DTO | `ErrorCode code`, `String message`, `List<FieldErrorResponse> fieldErrors`, `String requestId` | Единое тело ошибки REST API. |
| `FieldErrorResponse` | `presentation.error.model` | `record` | `String field`, `String code`, `String message` | Ошибка конкретного поля запроса. |
| `ErrorCode` | `presentation.error.model` | `enum` | стабильные машинные коды | Отделяет публичный код ошибки от текста и Java exception class. |
| `IdentityExceptionHandler` | `presentation.error.handler` | `@RestControllerAdvice` | exception handlers | Преобразует application/domain/validation исключения в HTTP status и `ApiError`. |
| `RestAuthenticationEntryPoint` | `presentation.error.handler` | component | `AuthenticationEntryPoint` | Возвращает `ApiError` для запроса без действительной аутентификации. |
| `RestAccessDeniedHandler` | `presentation.error.handler` | component | `AccessDeniedHandler` | Возвращает `ApiError` для аутентифицированного пользователя без требуемых прав. |

Минимальный набор `ErrorCode` для Identity: `VALIDATION_ERROR`, `UNAUTHENTICATED`, `INVALID_CREDENTIALS`, `EMAIL_NOT_VERIFIED`, `INVALID_REFRESH_TOKEN`, `FORBIDDEN`, `NOT_FOUND`, `EMAIL_ALREADY_EXISTS`, `INVALID_VERIFICATION_TOKEN`, `RATE_LIMIT_EXCEEDED`, `INTERNAL_ERROR`.

## Application: Commands и Query

### `identity.application.command.account`

| Элемент | Стереотип | Поля | Назначение |
|---|---|---|---|
| `RegisterUserCommand` | `record` | `String email`, `String rawPassword`, `String firstName`, `String lastName`, `Set<UserRole> roles` | Вход регистрации после HTTP mapping. |
| `UpdateCurrentUserCommand` | `record` | `UUID userId`, `String email`, `String firstName`, `String lastName` | Изменение текущего пользователя. `userId` формируется из security context, а не из тела запроса. |

### `identity.application.command.verification`

| Элемент | Стереотип | Поля | Назначение |
|---|---|---|---|
| `ConfirmEmailCommand` | `record` | `String rawToken` | Подтверждение текущего или ожидающего email. |
| `ResendEmailVerificationCommand` | `record` | `String email` | Повторный выпуск verification token. |

### `identity.application.command.authentication`

| Элемент | Стереотип | Поля | Назначение |
|---|---|---|---|
| `LoginCommand` | `record` | `String email`, `String rawPassword` | Вход по email и паролю. |
| `RefreshTokenCommand` | `record` | `String rawRefreshToken` | Ротация refresh token и выпуск новой пары токенов. Значение приходит из cookie. |
| `LogoutCommand` | `record` | `String rawRefreshToken` | Идемпотентный отзыв предъявленного refresh token. |

### `identity.application.query`

| Элемент | Стереотип | Поля | Назначение |
|---|---|---|---|
| `GetCurrentUserQuery` | `record` | `UUID userId` | Запрос данных текущего пользователя. |

## Application: Results и Models

### `identity.application.result`

| Элемент | Стереотип | Поля | Назначение |
|---|---|---|---|
| `RegistrationResult` | `record` | `String email`, `Instant verificationExpiresAt` | Результат регистрации до подтверждения email. |
| `ResendVerificationResult` | `record` | `String email`, `Instant verificationExpiresAt` | Нейтральный результат повторной отправки. Не раскрывает наличие аккаунта. |
| `AuthenticationResult` | `record` | `IssuedAccessToken accessToken`, `IssuedRefreshToken refreshToken`, `CurrentUserResult user` | Общий результат login, confirm и refresh. Presentation помещает refresh token в cookie. |
| `CurrentUserResult` | `record` | `UUID id`, `String email`, `String? pendingEmail`, `String firstName`, `String lastName`, `Set<UserRole> roles`, `UserStatus status`, `Instant? emailVerifiedAt`, `Instant createdAt`, `Instant updatedAt` | Представление пользователя на application-границе. |

### `identity.application.model`

| Элемент | Стереотип | Поля | Назначение |
|---|---|---|---|
| `IssuedAccessToken` | `record` | `String value`, `Instant expiresAt` | Выпущенный JWT для передачи клиенту. |
| `IssuedRefreshToken` | `record` | `String value`, `Instant expiresAt` | Выпущенное открытое значение refresh token. Живёт только до записи cookie и не сохраняется как есть. |
| `RefreshTokenState` | `record` | `UUID id`, `UUID userId`, `String tokenHash`, `UUID familyId`, `Instant expiresAt`, `Instant revokedAt`, `Instant createdAt` | Сохраняемое состояние refresh token. `revokedAt` может отсутствовать. |

## Application: Services

| Элемент | Пакет | Реализует | Ответственность |
|---|---|---|---|
| `RegisterUserService` | `service.account` | `RegisterUserUseCase` | Нормализует email, проверяет доступность, хеширует пароль, создаёт `User` и `EmailVerification`, сохраняет их, ставит письмо в очередь и публикует событие регистрации. |
| `GetCurrentUserService` | `service.account` | `GetCurrentUserUseCase` | Загружает пользователя по ID и преобразует в `CurrentUserResult`. |
| `UpdateCurrentUserService` | `service.account` | `UpdateCurrentUserUseCase` | Изменяет профиль; при смене email резервирует pending email и запускает новое подтверждение. |
| `IdentityQueryService` | `service.account` | `identity.api.IdentityQuery` | Реализует публичное чтение для других модулей и возвращает `UserSummary`. |
| `ConfirmEmailService` | `service.verification` | `ConfirmEmailUseCase` | Хеширует входной token, загружает активную verification, подтверждает email, активирует пользователя при регистрации, помечает verification использованной и выдаёт пару токенов. |
| `ResendEmailVerificationService` | `service.verification` | `ResendEmailVerificationUseCase` | Инвалидирует прежнюю verification, создаёт новую и ставит письмо в очередь; сохраняет нейтральный ответ для неизвестного email. |
| `LoginService` | `service.authentication` | `LoginUseCase` | Проверяет пароль и состояние пользователя, затем выпускает access и refresh tokens и сохраняет hash refresh token. |
| `RefreshTokenService` | `service.authentication` | `RefreshTokenUseCase` | Загружает token любого состояния по hash с row-level lock, проверяет expiry/revocation, ротирует активный token в той же family и выдаёт новый access token. Повторное использование отозванного token отзывает family по `userId + familyId`. |
| `LogoutService` | `service.authentication` | `LogoutUseCase` | Хеширует полученный refresh token и отзывает найденный токен. Операция идемпотентна. |

Application services не вызывают друг друга. Общие преобразования находятся в mapper, общие бизнес-правила — в domain, а технические операции — за output ports.

## Application: Mappers

| Элемент | Пакет | Основные операции | Назначение |
|---|---|---|---|
| `UserResultMapper` | `application.mapper` | `toCurrentUserResult(User)` | Преобразует агрегат `User` в application result. |
| `IdentityApiMapper` | `application.mapper` | `toUserSummary(User)`, `toIntegrationEvent(domainEvent)` | Изолирует публичные API-типы от domain-модели. |

## Application: Exceptions

| Элемент | Пакет | Когда возникает |
|---|---|---|
| `InvalidUseCaseInputException` | `application.exception` | Command/query нарушает контракт use case после внешней валидации или пришёл не из HTTP. |
| `UserNotFoundException` | `application.exception` | Пользователь, необходимый сценарию, не найден. |
| `EmailAlreadyExistsException` | `application.exception` | Email уже зарезервирован как `CURRENT` или `PENDING`; также преобразуется из database unique violation. |
| `InvalidCredentialsException` | `application.exception` | Login credentials неверны; причина не уточняется клиенту. |
| `EmailVerificationRequiredException` | `application.exception` | Вход запрещён до подтверждения email. |
| `InvalidVerificationTokenException` | `application.exception` | Verification token неизвестен, истёк, использован или отозван. |
| `InvalidRefreshTokenException` | `application.exception` | Refresh token неизвестен, истёк, отозван или повторно использован. |
| `AccountOperationNotAllowedException` | `application.exception` | Состояние аккаунта запрещает запрошенную операцию. |

## Domain: User aggregate

### `identity.domain.user.model.User`

Стереотип: `aggregate root`.

Поля:

```text
UUID id
Email email
Email pendingEmail [0..1]
PasswordHash passwordHash
String firstName
String lastName
Set<UserRole> roles
UserStatus status
Instant emailVerifiedAt [0..1]
Instant createdAt
Instant updatedAt
List<DomainEvent> domainEvents
```

Основные операции:

```text
register(...): User
verifyRegistrationEmail(Email, Instant): void
requestEmailChange(Email, Instant): void
confirmPendingEmail(Email, Instant): void
updateProfile(String, String, Instant): void
changePassword(PasswordHash, Instant): void
addRole(UserRole, Instant): void
suspend(Instant): void
deactivate(Instant): void
pullDomainEvents(): List<DomainEvent>
```

Инварианты:

- `email`, `passwordHash`, имя, фамилия и хотя бы одна роль обязательны;
- новый пользователь имеет статус `PENDING_EMAIL_VERIFICATION`;
- активировать пользователя можно только подтверждением registration email;
- `pendingEmail` не заменяет текущий email до подтверждения;
- current и pending email не могут совпадать;
- смена email не сбрасывает подтверждение старого адреса до подтверждения нового;
- пользователь со статусом `SUSPENDED` или `DEACTIVATED` не может login/refresh;
- недопустимые переходы состояния завершаются domain exception;
- изменения обновляют `updatedAt` и при необходимости создают domain event.

### Value objects и enums пользователя

| Элемент | Стереотип | Состав / значения | Назначение |
|---|---|---|---|
| `Email` | value object | `String value` | Нормализует email и проверяет базовый формат. Сравнение выполняется по нормализованному значению. |
| `PasswordHash` | value object | `String value` | Хранит только результат password hashing. Не принимает и не раскрывает открытый пароль. |
| `UserRole` | `enum` | `TEACHER`, `STUDENT` | Авторизационные роли Identity. Обе роли могут принадлежать одному пользователю. |
| `UserStatus` | `enum` | `PENDING_EMAIL_VERIFICATION`, `ACTIVE`, `SUSPENDED`, `DEACTIVATED` | Жизненный цикл аккаунта. |

### Доменные события пользователя

| Элемент | Стереотип | Поля | Назначение |
|---|---|---|---|
| `UserRegisteredDomainEvent` | domain event | `UUID userId`, `Email email`, `Instant occurredAt` | Создаётся при регистрации агрегата. |
| `UserActivatedDomainEvent` | domain event | `UUID userId`, `Email email`, `Instant occurredAt` | Создаётся при первом подтверждении email. |
| `UserAccountUpdatedDomainEvent` | domain event | `UUID userId`, `Set<String> changedFields`, `Instant occurredAt` | Создаётся при значимом изменении данных аккаунта. |

### Domain exceptions пользователя

| Элемент | Когда возникает |
|---|---|
| `InvalidEmailException` | Email пуст, не нормализуется или имеет недопустимый формат. |
| `InvalidUserDataException` | При создании/изменении нарушены инварианты данных пользователя. |
| `InvalidUserStateException` | Запрошен недопустимый переход состояния. |
| `EmailNotVerifiedException` | Операция требует подтверждённого email. |

## Domain: EmailVerification aggregate

### `identity.domain.verification.EmailVerification`

Стереотип: `aggregate root`.

Поля:

```text
UUID id
UUID userId
Email targetEmail
VerificationTokenHash tokenHash
VerificationPurpose purpose
Instant expiresAt
Instant consumedAt [0..1]
Instant invalidatedAt [0..1]
Instant createdAt
```

Основные операции:

```text
create(...): EmailVerification
isActiveAt(Instant): boolean
consume(Instant): void
invalidate(Instant): void
```

Инварианты:

- verification принадлежит одному пользователю, email и purpose;
- hash и срок действия обязательны;
- использованную, отозванную или истёкшую verification нельзя использовать;
- `consumedAt` и `invalidatedAt` не устанавливаются повторно.

### Остальные элементы verification

| Элемент | Стереотип | Состав / значения | Назначение |
|---|---|---|---|
| `VerificationTokenHash` | value object | `String value` | SHA-256 hash verification token, пригодный для сравнения и хранения. |
| `VerificationPurpose` | `enum` | `REGISTRATION`, `EMAIL_CHANGE` | Определяет действие после успешного подтверждения. |
| `InvalidEmailVerificationException` | domain exception | — | Нарушено состояние или правило жизненного цикла verification. |

## Infrastructure: Persistence adapters

| Элемент | Пакет | Реализует | Назначение |
|---|---|---|---|
| `JooqUserRepositoryAdapter` | `infrastructure.persistence.adapter` | `UserRepository` | Координирует `UserJooqRepository` и `UserPersistenceMapper`; возвращает и принимает только `User`. |
| `JooqEmailVerificationRepositoryAdapter` | `infrastructure.persistence.adapter` | `EmailVerificationRepository` | Преобразует `EmailVerification` в database representation и обратно. |
| `JooqRefreshTokenRepositoryAdapter` | `infrastructure.persistence.adapter` | `RefreshTokenRepository` | Сохраняет/загружает `RefreshTokenState`, управляет отзывом токена и family. |

## Infrastructure: Persistence mappers

| Элемент | Пакет | Основные операции | Назначение |
|---|---|---|---|
| `UserPersistenceMapper` | `infrastructure.persistence.mapper` | `toDomain(UserPersistenceData)`, `toPersistence(User)` | Собирает/разбирает агрегат пользователя из users, emails и roles. |
| `EmailVerificationPersistenceMapper` | `infrastructure.persistence.mapper` | `toDomain(record)`, `toRecord(model)` | Преобразует verification aggregate и jOOQ record/POJO. |
| `RefreshTokenPersistenceMapper` | `infrastructure.persistence.mapper` | `toApplication(record)`, `toRecord(state)` | Преобразует `RefreshTokenState` и jOOQ record/POJO. |

## Infrastructure: Persistence data

| Элемент | Пакет | Стереотип / поля | Назначение |
|---|---|---|---|
| `UserPersistenceData` | `infrastructure.persistence.data.model` | internal data carrier: `UsersRecord user`, `List<UserEmailsRecord> emails`, `List<UserRolesRecord> roles` | Объединяет строки нескольких таблиц перед восстановлением агрегата. Не покидает persistence. |
| `UserJooqRepository` | `infrastructure.persistence.data.repository` | repository | Выполняет SQL для `identity_users`, `identity_user_emails`, `identity_user_roles` через `DSLContext`; не импортирует domain. |
| `EmailVerificationJooqRepository` | `infrastructure.persistence.data.repository` | repository | Выполняет SQL для `identity_email_verifications`; работает с generated records/простыми data types. |
| `RefreshTokenJooqRepository` | `infrastructure.persistence.data.repository` | repository | Выполняет SQL для `identity_refresh_tokens`, включая блокировку/атомарную ротацию и отзыв family. |
| `generated` | `infrastructure.persistence.data.generated` | generated package | Содержит jOOQ tables, records и schema types, созданные из Flyway-схемы. Ручное редактирование запрещено. |

## Infrastructure: Security

| Элемент | Пакет | Реализует / тип | Назначение |
|---|---|---|---|
| `BCryptPasswordHasher` | `infrastructure.security.password` | `PasswordHasher` | Хеширует и проверяет пароли через BCrypt; strength задаётся конфигурацией. |
| `SpringJwtAccessTokenIssuer` | `infrastructure.security.token` | `AccessTokenIssuer` | Подписывает access JWT алгоритмом RS256. Claims: `sub`, `roles`, `iss`, `aud`, `iat`, `exp`. |
| `SecureRefreshTokenIssuer` | `infrastructure.security.token` | `RefreshTokenIssuer` | Создаёт криптографически случайный URL-safe refresh token достаточной энтропии и задаёт expiration. |
| `Sha256RefreshTokenHasher` | `infrastructure.security.token` | `RefreshTokenHasher` | Вычисляет стабильный SHA-256 hash refresh token перед поиском или сохранением. |
| `SecureVerificationTokenGenerator` | `infrastructure.security.token` | `VerificationTokenGenerator` | Создаёт криптографически случайный URL-safe verification token. |
| `Sha256VerificationTokenHasher` | `infrastructure.security.token` | `VerificationTokenHasher` | Вычисляет SHA-256 hash verification token. |
| `IdentityJwtAuthenticationConverter` | `infrastructure.security.authentication` | `Converter<Jwt, AbstractAuthenticationToken>` | Читает `sub` и `roles`, создаёт `JwtAuthenticationToken`, добавляет ожидаемый authority prefix. |
| `SecurityConfiguration` | `infrastructure.security.configuration` | `@Configuration` | Настраивает stateless `SecurityFilterChain`, Resource Server, CORS, CSRF-решение и публичные endpoints; принимает стандартные security handlers. |
| `JwtConfiguration` | `infrastructure.security.configuration` | `@Configuration` | Создаёт `JwtEncoder`, `JwtDecoder` и связанные beans из внешних RSA keys и properties. |
| `IdentityTokenProperties` | `infrastructure.security.configuration` | `@ConfigurationProperties` | Хранит `issuer`, `audience`, access/refresh/verification TTL, RSA key locations и cookie security settings. |

## Infrastructure: Messaging и Time

| Элемент | Пакет | Реализует | Назначение |
|---|---|---|---|
| `NotificationVerificationEmailAdapter` | `infrastructure.messaging.email` | `VerificationEmailSender` | Формирует confirmation URL, преобразует purpose и вызывает `notifications.api.NotificationGateway.enqueue(...)`. |
| `IdentityNotificationProperties` | `infrastructure.messaging.email` | `@ConfigurationProperties` | Хранит базовый frontend URL и относительный путь страницы подтверждения. |
| `SpringIntegrationEventPublisher` | `infrastructure.messaging.event` | `IntegrationEventPublisher` | Публикует публичные события через Spring `ApplicationEventPublisher`; подписчики обрабатывают их после commit. |
| `SystemTimeProvider` | `infrastructure.time` | `TimeProvider` | Возвращает `Instant.now(clock)` через внедрённый `java.time.Clock`, что делает время тестируемым. |

## Внешний публичный контракт Notifications

| Элемент | Пакет | Стереотип / поля | Назначение |
|---|---|---|---|
| `SendVerificationEmailCommand` | `notifications.api.command` | `record`: `String recipientEmail`, `URI confirmationUrl`, `VerificationEmailPurpose purpose`, `Instant expiresAt` | Типизированная команда постановки письма в очередь доставки. |
| `VerificationEmailPurpose` | `notifications.api.model` | `enum`: `REGISTRATION`, `EMAIL_CHANGE` | Публичное назначение письма без зависимости Notifications от domain Identity. |

`NotificationGateway` описан в [каталоге интерфейсов](interfaces.md).
