# Каталог классов и типов

Каталог выполняет роль переносимого Class Repository. Полное имя элемента образуется как `identity.<пакет>.<имя>`. Для DTO и неизменяемых моделей предпочтительны Java `record`; для enum указан набор значений; для сервисов и адаптеров указана их основная ответственность.

## Публичный API

### `identity.api.model`

| Элемент | Стереотип | Поля | Назначение |
|---|---|---|---|
| `UserSummary` | `record` | `UUID id`, `String email`, `String firstName`, `String lastName`, `Set<UserRoleView> roles`, `UserStatusView status` | Безопасное публичное представление пользователя для других модулей. Не раскрывает password hash, pending email и token data. |
| `UserRoleView` | `enum` | `TEACHER`, `STUDENT` | Публичное представление роли. Один пользователь может иметь обе роли. |
| `UserStatusView` | `enum` | `PENDING_EMAIL_VERIFICATION`, `ACTIVE`, `SUSPENDED`, `DEACTIVATED` | Публичное Java-представление состояния учётной записи; синхронизировано с HTTP/OpenAPI `UserStatus`. |
| `AccountEmailVerificationPurpose` | `enum` | `REGISTRATION`, `EMAIL_CHANGE` | Публичная причина подтверждения account email, не раскрывающая domain enum за границей модуля. |

### `identity.api.command`

| Элемент | Стереотип | Поля | Назначение |
|---|---|---|---|
| `RegistrationData` | `record` | email, raw password, имя, фамилия, `birthDate`, `Set<UserRoleView> roles` | Публичный вход `IdentityRegistrationGateway`; адаптер переводит его во внутреннюю команду. Пароль скрыт в `toString()`. |
| `RegistrationReceipt` | `record` | `UUID userId`, `String email`, `Instant verificationExpiresAt` | Публичный результат для RegistrationWorkflow; не является HTTP-ответом. |
| `AddUserRoleCommand` | `record` | `UUID userId`, `UserRoleView role` | Публичная команда `IdentityRoleGateway`. |

### `identity.api.event`

| Элемент | Стереотип | Поля | Назначение |
|---|---|---|---|
| `UserRegisteredEvent` | `record`, integration event | `UUID eventId`, `UUID userId`, `String email`, `Instant occurredAt` | Сообщает другим модулям, что пользователь зарегистрирован и ожидает подтверждения. |
| `UserActivatedEvent` | `record`, integration event | `UUID eventId`, `UUID userId`, `String email`, `Instant occurredAt` | Сообщает, что email подтверждён и пользователь активирован. |
| `UserAccountUpdatedEvent` | `record`, integration event | `UUID eventId`, `UUID userId`, `Set<String> changedFields`, `Instant occurredAt` | Сообщает об изменении публично значимых данных пользователя. |
| `AccountEmailVerifiedEvent` | `record`, integration event | `UUID eventId`, `UUID userId`, `String email`, `AccountEmailVerificationPurpose purpose`, `Instant occurredAt` | Сообщает о подтверждении владения конкретным account email при регистрации или смене адреса. |

Публичные события являются стабильными контрактами. Они создаются из доменных событий или результата use case, но не содержат ссылки на domain-типы.

## Presentation: Auth

### `identity.presentation.auth.model.request`

| Элемент | Стереотип | Поля | Назначение |
|---|---|---|---|
| `RegisterRequest` | `record`, request DTO | `String email`, `String password`, `String firstName`, `String lastName`, `LocalDate birthDate`, `List<RegistrationRole> roles` | JSON-запрос регистрации. Список сохраняет дубли до Bean Validation, чтобы выполнить OpenAPI `uniqueItems`; mapper передаёт во внутреннюю command уже множество. Password скрыт в `toString()`. |
| `LoginRequest` | `record`, request DTO | `String email`, `String password` | JSON-запрос входа. Открытый пароль живёт только в пределах обработки запроса. |
| `ConfirmEmailRequest` | `record`, request DTO | `String token` | Запрос подтверждения email по открытому verification token. |
| `ResendEmailVerificationRequest` | `record`, request DTO | `String email` | Запрос повторного выпуска verification token. Ответ не должен позволять определить существование email. |

### `identity.presentation.auth.model.response`

| Элемент | Стереотип | Поля | Назначение |
|---|---|---|---|
| `TokenResponse` | `record`, response DTO | `String accessToken`, `String tokenType`, `long expiresInSeconds` | Тело успешного login/confirm/refresh. Refresh token отсутствует в JSON и записывается в cookie. |
| `VerificationPendingResponse` | `record`, response DTO | `String email`, `Instant verificationExpiresAt` | Ответ регистрации до подтверждения email. Не раскрывает userId. Resend по публичному контракту возвращает нейтральный `202` без тела. |

### Контроллеры и вспомогательные классы Auth

| Элемент | Пакет | Стереотип | Назначение |
|---|---|---|---|
| `AuthController` | `presentation.auth.controller` | `@RestController` | Обрабатывает register, login, refresh и идемпотентный logout. Вызывает только application input ports; refresh cookie создаёт, ротирует или очищает через `RefreshTokenCookieFactory`. |
| `EmailVerificationController` | `presentation.auth.controller` | `@RestController` | Реализует confirm и resend. Confirm возвращает access token и refresh cookie; resend всегда отвечает нейтральным `202` без тела. |
| `AuthPresentationMapper` | `presentation.auth.mapper` | mapper | Преобразует auth request DTO в application commands, а application results — в response DTO. Не содержит бизнес-правил. |
| `RefreshTokenCookieFactory` | `presentation.auth.cookie` | component | Создаёт и очищает refresh cookie. Централизует `HttpOnly`, configurable `Secure`, `SameSite=Lax`, path `/api/v1/auth` и фактический оставшийся срок жизни. |

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
| `CurrentUserController` | `presentation.account.controller` | `@RestController` | Реализует `PATCH /api/v1/me`; user ID получает из проверенного `Authentication`. Составной `GET /api/v1/me` будет добавлен вместе с внешней query facade. |
| `UserPresentationMapper` | `presentation.account.mapper` | mapper | Преобразует `UpdateCurrentUserRequest` в command и `CurrentUserResult` в `UserResponse`. |
| `ValidAccountUpdate` | `presentation.account.validation` | `@Constraint` | Аннотация составной валидации `UpdateCurrentUserRequest`. |
| `AccountUpdateValidator` | `presentation.account.validation` | `ConstraintValidator` | Проверяет, что указан хотя бы один изменяемый атрибут и что сочетание значений допустимо на уровне HTTP-контракта. |

## Presentation: Error

| Элемент | Пакет | Стереотип | Поля / интерфейс | Назначение |
|---|---|---|---|---|
| `ApiError` | `presentation.error.model` | `record`, response DTO | `ErrorCode code`, `String message`, `List<FieldErrorResponse> fieldErrors`, `String requestId` | Единое тело ошибки REST API. |
| `FieldErrorResponse` | `presentation.error.model` | `record` | `String field`, `String code`, `String message` | Ошибка конкретного поля запроса. |
| `ErrorCode` | `presentation.error.model` | `enum` | стабильные машинные коды | Отделяет публичный код ошибки от текста и Java exception class. |
| `IdentityExceptionHandler` | `presentation.error.handler` | `@RestControllerAdvice` | exception handlers | Для реализованных auth-контроллеров преобразует JSON/Bean Validation, application и неожиданные исключения в `400`, `409` или `500` с единым `ApiError`. |
| `RestAuthenticationEntryPoint` | `presentation.error.handler` | component | `AuthenticationEntryPoint` | Возвращает `ApiError` для запроса без действительной аутентификации. |
| `RestAccessDeniedHandler` | `presentation.error.handler` | component | `AccessDeniedHandler` | Возвращает `ApiError` для аутентифицированного пользователя без требуемых прав. |

Минимальный набор `ErrorCode` для Identity: `VALIDATION_ERROR`, `UNAUTHENTICATED`, `INVALID_CREDENTIALS`, `EMAIL_NOT_VERIFIED`, `INVALID_REFRESH_TOKEN`, `FORBIDDEN`, `NOT_FOUND`, `EMAIL_ALREADY_EXISTS`, `INVALID_VERIFICATION_TOKEN`, `RATE_LIMIT_EXCEEDED`, `INTERNAL_ERROR`.

## Application: Commands и Query

### `identity.application.command.account`

| Элемент | Стереотип | Поля | Назначение |
|---|---|---|---|
| `RegistrationRole` | `enum` | `TEACHER`, `STUDENT` | Внутреннее application-представление роли. |
| `RegisterUserCommand` | `record` | `String email`, `String rawPassword`, `String firstName`, `String lastName`, `LocalDate birthDate`, `Set<RegistrationRole> roles` | Вход единственного application use case регистрации. Сервис преобразует роли в доменный `UserRole`. |
| `AssignRoleCommand` | `record` | `UUID userId`, `RegistrationRole role` | Вход внутреннего use case добавления роли. |
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
| `LogoutCommand` | `record` | `String rawRefreshToken` | Идемпотентный отзыв session-family предъявленного refresh token; значение может отсутствовать. |

### `identity.application.query`

| Элемент | Стереотип | Поля | Назначение |
|---|---|---|---|
| `GetCurrentUserQuery` | `record` | `UUID userId` | Запрос данных текущего пользователя. |

## Application: Results и Models

### `identity.application.result`

| Элемент | Стереотип | Поля | Назначение |
|---|---|---|---|
| `RegistrationResult` | `record` | `UUID userId`, `String email`, `Instant verificationExpiresAt` | Внутренний результат регистрации до подтверждения email. Публичный адаптер преобразует его в `RegistrationReceipt`; `userId` не включается в HTTP-ответ. |
| `ResendVerificationResult` | `record` | `String email`, `Instant verificationExpiresAt` | Нейтральный результат повторной отправки. Не раскрывает наличие аккаунта. |
| `AuthenticationResult` | `record` | `IssuedAccessToken accessToken`, `IssuedRefreshToken refreshToken` | Общий результат confirm, login и refresh. Пользователь загружается отдельно через `GET /me`; Presentation помещает refresh token в cookie. |
| `CurrentUserResult` | `record` | `UUID id`, `String email`, `String? pendingEmail`, `String firstName`, `String lastName`, `Set<UserRole> roles`, `UserStatus status`, `Instant? emailVerifiedAt`, `Instant createdAt`, `Instant updatedAt` | Представление пользователя на application-границе. |

### `identity.application.model`

| Элемент | Стереотип | Поля | Назначение |
|---|---|---|---|
| `IssuedAccessToken` | `record` | `String value`, `Instant expiresAt` | Выпущенный JWT для передачи клиенту. |
| `IssuedRefreshToken` | `record` | `String value`, `Instant expiresAt` | Выпущенное открытое значение refresh token. Живёт только до записи cookie и не сохраняется как есть. |
| `RefreshSession` | `record` | `IssuedRefreshToken token`, `RefreshTokenState state` | Связывает raw token для cookie с сохраняемым hash-состоянием одной новой refresh-family. |
| `RefreshTokenState` | `record` | `UUID id`, `UUID userId`, `String tokenHash`, `UUID familyId`, `Instant expiresAt`, `Instant revokedAt`, `Instant createdAt` | Сохраняемое состояние refresh token. `revokedAt` может отсутствовать. |

## Application: Services

| Элемент | Пакет | Реализует | Ответственность |
|---|---|---|---|
| `RegisterUserService` | `service.account` | `RegisterUserUseCase` | Нормализует email, проверяет доступность, хеширует пароль, создаёт `User` и `EmailVerification`, сохраняет их, ставит письмо в очередь и публикует событие регистрации. |
| `GetCurrentUserService` | `service.account` | `GetCurrentUserUseCase` | Запланирован вместе с полным `GET /me`; до появления Tutoring не реализуется как неиспользуемый внутренний сервис. |
| `UpdateCurrentUserService` | `service.account` | `UpdateCurrentUserUseCase` | Под row lock изменяет имя/фамилию; для действительно нового email резервирует `pendingEmail`, инвалидирует прежнюю `EMAIL_CHANGE` verification, создаёт новую на 5 минут и ставит письмо в очередь одной транзакцией. Текущий и уже pending email обрабатываются как no-op. |
| `IdentityQueryService` | `service.account` | `identity.api.query.IdentityQuery` | Реализует read-only межмодульное чтение по ID и возвращает безопасный `UserSummary`; не является HTTP endpoint. |
| `ConfirmEmailService` | `service.verification` | `ConfirmEmailUseCase` | В порядке `User → EmailVerification` блокирует изменяемые строки, подтверждает email, consume verification, создаёт новую refresh family, сохраняет только hash refresh token, выдаёт access/refresh и публикует события. |
| `ResendEmailVerificationService` | `service.verification` | `ResendEmailVerificationUseCase` | После lock User выбирает `REGISTRATION` для CURRENT email неподтверждённого аккаунта или `EMAIL_CHANGE` для PENDING email активного аккаунта. Инвалидирует прежнюю verification purpose, создаёт новую и ставит письмо в очередь; остальные адреса получают нейтральный no-op. |
| `LoginService` | `service.authentication` | `LoginUseCase` | Всегда выполняет BCrypt-проверку, не различает неизвестный email и неверный пароль, проверяет status только после пароля, затем выпускает access/refresh и сохраняет hash refresh token новой family. |
| `RefreshSessionFactory` | `service.authentication` | — | Общий алгоритм выпуска raw refresh token и создания `RefreshTokenState`: `create` начинает новую family для confirm/login, `rotate` сохраняет family и `expiresAt`; сам ничего не сохраняет. |
| `RefreshTokenService` | `service.authentication` | `RefreshTokenUseCase` | В порядке `User → RefreshToken` повторно валидирует token под row lock, ротирует active token с прежними family/expiry и выдаёт access JWT. Reuse или non-active account фиксирует отзыв family даже при ответе `401/403`. |
| `LogoutService` | `service.authentication` | `LogoutUseCase` | Хеширует token, в порядке `User → RefreshToken` повторно сверяет запись под row lock и отзывает всю найденную family. Пустое, неизвестное или уже отозванное значение обрабатывается идемпотентно. |

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
| `RefreshAccessDeniedException` | `application.exception` | Token принадлежит аккаунту, состояние которого запрещает продолжать refresh-сессию. |
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
List<UserDomainEvent> domainEvents
```

Основные операции:

```text
register(...): User
reconstitute(...): User
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
| `UserDomainEvent` | interface | `Instant occurredAt()` | Общий внутренний тип событий агрегата `User`. |
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

### `identity.domain.verification.model.EmailVerification`

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
| `VerificationTokenHash` | value object | `String value` | SHA-256 hash verification token в каноническом lowercase hex-формате из 64 символов. |
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
| `EmailVerificationPersistenceMapper` | `infrastructure.persistence.mapper` | `toDomain(record)`, `toPersistence(model)` | Преобразует verification aggregate и jOOQ record. |
| `RefreshTokenPersistenceMapper` | `infrastructure.persistence.mapper` | `toApplication(record)`, `toPersistence(state)` | Преобразует `RefreshTokenState` и jOOQ record. |
| `InvalidPersistenceDataException` | `infrastructure.persistence.exception` | infrastructure exception | Прочитанные persistence-данные невозможно собрать в корректную domain/application-модель. |

## Infrastructure: Persistence data

| Элемент | Пакет | Стереотип / поля | Назначение |
|---|---|---|---|
| `UserPersistenceData` | `infrastructure.persistence.data.model` | internal data carrier: `IdentityUsersRecord user`, `List<IdentityUserEmailsRecord> emails`, `List<IdentityUserRolesRecord> roles` | Объединяет строки нескольких таблиц перед восстановлением агрегата. Не покидает persistence. |
| `UserJooqRepository` | `infrastructure.persistence.data.repository` | `findById`, `findByIdForUpdate`, `findByCurrentEmail`, `findByAnyEmail`, `existsByEmail`, `save` | Выполняет SQL для `identity_users`, `identity_user_emails`, `identity_user_roles` через `DSLContext`; current-only lookup используется login, lookup обоих kinds — verification; блокирующее чтение и save требуют внешнюю транзакцию. |
| `EmailVerificationJooqRepository` | `infrastructure.persistence.data.repository` | `findActiveByTokenHashForUpdate`, `save`, `invalidateActiveForUser` | Выполняет SQL для `identity_email_verifications`; locking read и writes требуют внешнюю транзакцию; работает с generated records/простыми data types. |
| `RefreshTokenJooqRepository` | `infrastructure.persistence.data.repository` | `findByTokenHash`, `findByTokenHashForUpdate`, `save`, `revoke`, `revokeFamily` | Выполняет SQL для `identity_refresh_tokens`: предварительно находит владельца без блокировки, повторно читает token с row lock, вставляет новое состояние и отзывает token/family. Все операции требуют внешнюю транзакцию. |
| `generated` | `infrastructure.persistence.data.generated` | generated package | Содержит jOOQ tables, records и schema types, созданные из Flyway-схемы. Ручное редактирование запрещено. |

## Infrastructure: Security

| Элемент | Пакет | Реализует / тип | Назначение |
|---|---|---|---|
| `BCryptPasswordHasher` | `infrastructure.security.password` | `PasswordHasher` | Хеширует и проверяет пароли через BCrypt; strength `10` задаётся конфигурацией. Защитно отклоняет пароль длиннее 72 UTF-8 байт. |
| `SpringJwtAccessTokenIssuer` | `infrastructure.security.token` | `AccessTokenIssuer` | Подписывает access JWT алгоритмом RS256. Claims: `sub`, `roles`, `iss`, `aud`, `iat`, `exp`. |
| `SecureRefreshTokenIssuer` | `infrastructure.security.token` | `RefreshTokenIssuer` | Создаёт криптографически случайный URL-safe refresh token достаточной энтропии и задаёт expiration. |
| `Sha256RefreshTokenHasher` | `infrastructure.security.token` | `RefreshTokenHasher` | Вычисляет стабильный SHA-256 hash refresh token перед поиском или сохранением. |
| `SecureVerificationTokenGenerator` | `infrastructure.security.token` | `VerificationTokenGenerator` | Через `SecureRandom` создаёт verification token из 32 случайных байт и кодирует его URL-safe Base64 без padding. |
| `Sha256VerificationTokenHasher` | `infrastructure.security.token` | `VerificationTokenHasher` | Вычисляет стабильный SHA-256 hash verification token и возвращает lowercase hex из 64 символов. |
| `IdentityJwtAuthenticationConverter` | `infrastructure.security.authentication` | `Converter<Jwt, AbstractAuthenticationToken>` | Читает `sub` и `roles`, создаёт `JwtAuthenticationToken`, добавляет ожидаемый authority prefix. |
| `CookieCredentialOriginFilter` | `infrastructure.security.request` | `OncePerRequestFilter` | Для POST refresh/logout требует присутствующий `Origin` из configured allowlist до обработки cookie credential и CORS. |
| `SecurityConfiguration` | `infrastructure.security.configuration` | `@Configuration` | Настраивает stateless `SecurityFilterChain`, Resource Server, CORS, CSRF-решение и публичные endpoints; принимает стандартные security handlers. |
| `JwtConfiguration` | `infrastructure.security.configuration` | `@Configuration` | Получает внешние RSA-ключи через Spring `@Value`, создаёт `JwtEncoder`, `JwtDecoder` и `AccessTokenIssuer`. Encoder использует `NimbusJwtEncoder.withKeyPair(...)`; decoder принимает только RS256, требует `exp` и использует clock skew `0`; `iss`/`aud` в MVP отдельно не валидируются. |
| `IdentityCorsProperties` | `infrastructure.security.configuration` | `@ConfigurationProperties` | Хранит непустой allowlist frontend origins для CORS. |
| `IdentityPasswordProperties` | `infrastructure.security.configuration` | `@ConfigurationProperties` | Хранит BCrypt strength из `identity.security.password`; текущее значение — `10`. |
| `IdentityTokenProperties` | `infrastructure.security.configuration` | `@ConfigurationProperties` | Хранит verification/access/refresh TTL, entropy bytes, issuer и audience из `identity.token`. |

## Infrastructure: Messaging и Time

| Элемент | Пакет | Реализует | Назначение |
|---|---|---|---|
| `NotificationVerificationEmailAdapter` | `infrastructure.messaging.email` | `VerificationEmailSender` | Формирует confirmation URL, явно преобразует Identity purpose в публичный Notifications purpose и вызывает `notifications.api.NotificationGateway.enqueue(...)`; регистрируется через `IdentityConfiguration`. |
| `IdentityNotificationProperties` | `infrastructure.messaging.email` | `@ConfigurationProperties` | Хранит `identity.notification.frontend-base-url` и относительный путь страницы подтверждения; проверяет абсолютный HTTP(S) URL и корректный path. |
| `SpringIntegrationEventPublisher` | `infrastructure.messaging.event` | `IntegrationEventPublisher` | Передаёт публичные события без преобразования в Spring `ApplicationEventPublisher`. Публикация сама по себе синхронна; подписчики, которым нужны зафиксированные данные, используют `@TransactionalEventListener(AFTER_COMMIT)`. |
| `IdentityConfiguration` | `infrastructure.configuration` | `@Configuration` | Собирает `IdentityApiMapper`, `VerificationEmailSender` и транзакционный `RegisterUserUseCase` из существующих output-port adapters и configuration properties. |
| `TimeConfiguration` | `io.github.edtechdevelopment` | `@Configuration` | В composition root предоставляет единый production `Clock.systemUTC()` для модулей. |
| `SystemTimeProvider` | `infrastructure.time` | `TimeProvider` | Возвращает время внедрённого `java.time.Clock`, нормализованное до микросекундной точности PostgreSQL, что сохраняет равенство timestamp в HTTP и БД и делает время тестируемым. |

## Внешний публичный контракт Notifications

| Элемент | Пакет | Стереотип / поля | Назначение |
|---|---|---|---|
| `NotificationGateway` | `notifications.api` | public module interface | Принимает типизированную команду через `enqueue(...)`; persistence и SMTP остаются внутри Notifications. |
| `SendVerificationEmailCommand` | `notifications.api.command` | `record`: `String recipientEmail`, `URI confirmationUrl`, `VerificationEmailPurpose purpose`, `Instant expiresAt` | Типизированная команда постановки письма в очередь доставки. `expiresAt` повторяет deadline token, чтобы Notifications не отправлял уже бесполезную ссылку; `toString()` скрывает sensitive payload. |
| `VerificationEmailPurpose` | `notifications.api.model` | `enum`: `REGISTRATION`, `EMAIL_CHANGE` | Публичное назначение письма без зависимости Notifications от domain Identity. |

`NotificationGateway` описан в [каталоге интерфейсов](interfaces.md).

## Внутренняя очередь Notifications

| Элемент | Пакет | Реализует / тип | Назначение |
|---|---|---|---|
| `VerificationEmailDelivery` | `notifications.domain.delivery.model` | domain model | Создаёт новое задание в `PENDING`, восстанавливает сохранённое состояние через `reconstitute(...)` и защищает переходы `PENDING → PROCESSING`, `PROCESSING → SENT/PENDING/FAILED`, `PENDING/PROCESSING → EXPIRED`. Проверяет временные границы и инварианты каждого статуса; сохраняет confirmation URL только пока возможна отправка или retry; защищённый `toString()` не раскрывает получателя и URL. |
| `DeliveryStatus` | `notifications.domain.delivery.model` | `enum` | Состояния `PENDING`, `PROCESSING`, `SENT`, `FAILED`, `EXPIRED`; допустимые переходы контролирует `VerificationEmailDelivery`. |
| `VerificationEmailDeliveryPurpose` | `notifications.domain.delivery.model` | `enum` | Внутреннее назначение доставки без зависимости domain от публичного API. |
| `EnqueueVerificationEmailService` | `notifications.application.service` | `NotificationGateway` | В уже существующей транзакции создаёт `PENDING` delivery и сохраняет его; использует `Propagation.MANDATORY` и регистрируется через `NotificationsConfiguration`. Класс не `final`, потому что Spring создаёт class-based transaction proxy. |
| `ProcessVerificationEmailDeliveriesService` | `notifications.application.service` | application service | Одним batch обслуживает expired/stale задания, фиксирует `PROCESSING` в короткой транзакции, вызывает sender вне транзакции БД и отдельно сохраняет `SENT`, retry `PENDING`, `FAILED` или `EXPIRED`; явно регистрируется через `NotificationsConfiguration`. |
| `TimeProvider` | `notifications.application.port.out` | output port | Даёт application-сервису тестируемое текущее время, не импортируя внутренний порт Identity. |
| `VerificationEmailSender` | `notifications.application.port.out.email` | output port | Отправляет подготовленное verification-письмо; SMTP-адаптер обязан классифицировать сбои как temporary или permanent. |
| `VerificationEmailMessage` | `notifications.application.port.out.email` | `record` | Передаёт отправителю email получателя, confirmation URL и назначение письма без дублирования срока жизни токена. |
| `TemporaryEmailDeliveryException` | `notifications.application.exception` | exception | Обозначает временный сбой отправки, после которого delivery возвращается в `PENDING`, пока не истёк. |
| `PermanentEmailDeliveryException` | `notifications.application.exception` | exception | Обозначает окончательный отказ отправки, после которого актуальный delivery переходит в `FAILED`. |
| `VerificationEmailDeliveryRepository` | `notifications.application.port.out.persistence` | output port | Создаёт и обновляет delivery, выбирает ожидающие, зависшие в `PROCESSING` и истёкшие задания ограниченными порциями. |
| `JooqVerificationEmailDeliveryRepositoryAdapter` | `notifications.infrastructure.persistence.adapter` | `VerificationEmailDeliveryRepository` | Преобразует domain model и generated record в обе стороны, переводит `Instant` в тип времени БД и делегирует операции jOOQ repository. |
| `VerificationEmailDeliveryPersistenceMapper` | `notifications.infrastructure.persistence.mapper` | mapper | Преобразует доменную модель и jOOQ record в обе стороны: `Instant`/UTC `OffsetDateTime`, enum/строки БД и `URI`/строку confirmation URL. |
| `VerificationEmailDeliveryJooqRepository` | `notifications.infrastructure.persistence.data.repository` | jOOQ repository | Выполняет явные `INSERT`, выборки `PENDING`, stale `PROCESSING`, expired-заданий и `UPDATE` изменяемого состояния в `notification_email_deliveries`; все операции требуют внешнюю транзакцию. |
| `SpringMailVerificationEmailSender` | `notifications.infrastructure.messaging.email` | `VerificationEmailSender` | Формирует текстовое verification-письмо через `SimpleMailMessage`, выбирает тему/текст по purpose и преобразует Spring Mail failures в temporary/permanent application exceptions. Локально отправляет в Mailpit, в production — во внешний SMTP через конфигурацию `JavaMailSender`. |
| `VerificationEmailDeliveryScheduler` | `notifications.infrastructure.scheduling` | scheduler | С `fixedDelay` вызывает один processing batch; неожиданную ошибку логирует без recipient/token и оставляет scheduler живым. Создаётся только при `scheduler-enabled=true`. |
| `NotificationsConfiguration` | `notifications.infrastructure.configuration` | `@Configuration` | Создаёт module-owned `TimeProvider`, Spring Mail sender, processing service и условный scheduler; публикует `EnqueueVerificationEmailService` как `NotificationGateway`. |
| `NotificationDeliveryProperties` | `notifications.infrastructure.configuration` | `@ConfigurationProperties` | Валидирует `batch-size`, `processing-timeout`, `poll-delay`, `initial-delay` и флаг scheduler-а; текущие MVP-значения — `10`, `1m`, `10s`, `10s`, `true`. |
| `NotificationMailProperties` | `notifications.infrastructure.configuration` | `@ConfigurationProperties` | Валидирует адрес отправителя; локально используется `no-reply@edtech.local`, production обязан переопределить его настоящим адресом. |
| `SystemTimeProvider` | `notifications.infrastructure.time` | `TimeProvider` | Адаптирует общий application `Clock` к внутреннему time port Notifications и нормализует `Instant` до микросекундной точности PostgreSQL. |
