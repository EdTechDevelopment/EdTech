# Структура пакетов Identity

## Полное дерево

```text
identity
├── api
│   ├── IdentityQuery
│   ├── model
│   │   ├── UserSummary
│   │   ├── UserRoleView
│   │   └── UserStatusView
│   └── event
│       ├── UserRegisteredEvent
│       ├── UserActivatedEvent
│       └── UserAccountUpdatedEvent
├── presentation
│   ├── auth
│   │   ├── controller
│   │   │   ├── AuthController
│   │   │   └── EmailVerificationController
│   │   ├── model
│   │   │   ├── request
│   │   │   │   ├── RegisterRequest
│   │   │   │   ├── LoginRequest
│   │   │   │   ├── ConfirmEmailRequest
│   │   │   │   └── ResendEmailVerificationRequest
│   │   │   └── response
│   │   │       ├── TokenResponse
│   │   │       └── VerificationPendingResponse
│   │   ├── mapper
│   │   │   └── AuthPresentationMapper
│   │   └── cookie
│   │       └── RefreshTokenCookieFactory
│   ├── account
│   │   ├── controller
│   │   │   └── CurrentUserController
│   │   ├── model
│   │   │   ├── request
│   │   │   │   └── UpdateCurrentUserRequest
│   │   │   └── response
│   │   │       └── UserResponse
│   │   ├── mapper
│   │   │   └── UserPresentationMapper
│   │   └── validation
│   │       ├── ValidAccountUpdate
│   │       └── AccountUpdateValidator
│   └── error
│       ├── handler
│       │   ├── IdentityExceptionHandler
│       │   ├── RestAuthenticationEntryPoint
│       │   └── RestAccessDeniedHandler
│       └── model
│           ├── ApiError
│           ├── FieldErrorResponse
│           └── ErrorCode
├── application
│   ├── port
│   │   ├── in
│   │   │   ├── account
│   │   │   │   ├── RegisterUserUseCase
│   │   │   │   ├── GetCurrentUserUseCase
│   │   │   │   └── UpdateCurrentUserUseCase
│   │   │   ├── verification
│   │   │   │   ├── ConfirmEmailUseCase
│   │   │   │   └── ResendEmailVerificationUseCase
│   │   │   └── authentication
│   │   │       ├── LoginUseCase
│   │   │       ├── RefreshTokenUseCase
│   │   │       └── LogoutUseCase
│   │   ├── out
│   │   │   ├── persistence
│   │   │   │   ├── UserRepository
│   │   │   │   ├── EmailVerificationRepository
│   │   │   │   └── RefreshTokenRepository
│   │   │   ├── security
│   │   │   │   ├── PasswordHasher
│   │   │   │   ├── VerificationTokenGenerator
│   │   │   │   ├── VerificationTokenHasher
│   │   │   │   ├── AccessTokenIssuer
│   │   │   │   ├── RefreshTokenIssuer
│   │   │   │   └── RefreshTokenHasher
│   │   │   └── messaging
│   │   │       ├── VerificationEmailSender
│   │   │       └── IntegrationEventPublisher
│   │   └── TimeProvider
│   ├── command
│   │   ├── account
│   │   │   ├── RegistrationRole
│   │   │   ├── RegisterUserCommand
│   │   │   └── UpdateCurrentUserCommand
│   │   ├── verification
│   │   │   ├── ConfirmEmailCommand
│   │   │   └── ResendEmailVerificationCommand
│   │   └── authentication
│   │       ├── LoginCommand
│   │       ├── RefreshTokenCommand
│   │       └── LogoutCommand
│   ├── query
│   │   └── GetCurrentUserQuery
│   ├── result
│   │   ├── RegistrationResult
│   │   ├── ResendVerificationResult
│   │   ├── AuthenticationResult
│   │   └── CurrentUserResult
│   ├── model
│   │   ├── IssuedAccessToken
│   │   ├── IssuedRefreshToken
│   │   └── RefreshTokenState
│   ├── service
│   │   ├── account
│   │   │   ├── RegisterUserService
│   │   │   ├── GetCurrentUserService
│   │   │   ├── UpdateCurrentUserService
│   │   │   └── IdentityQueryService
│   │   ├── verification
│   │   │   ├── ConfirmEmailService
│   │   │   └── ResendEmailVerificationService
│   │   └── authentication
│   │       ├── LoginService
│   │       ├── RefreshTokenService
│   │       └── LogoutService
│   ├── mapper
│   │   ├── UserResultMapper
│   │   └── IdentityApiMapper
│   └── exception
│       ├── InvalidUseCaseInputException
│       ├── UserNotFoundException
│       ├── EmailAlreadyExistsException
│       ├── InvalidCredentialsException
│       ├── EmailVerificationRequiredException
│       ├── InvalidVerificationTokenException
│       ├── InvalidRefreshTokenException
│       └── AccountOperationNotAllowedException
├── domain
│   ├── user
│   │   ├── model
│   │   │   ├── User
│   │   │   ├── Email
│   │   │   ├── PasswordHash
│   │   │   ├── UserRole
│   │   │   └── UserStatus
│   │   ├── event
│   │   │   ├── UserDomainEvent
│   │   │   ├── UserRegisteredDomainEvent
│   │   │   ├── UserActivatedDomainEvent
│   │   │   └── UserAccountUpdatedDomainEvent
│   │   └── exception
│   │       ├── InvalidEmailException
│   │       ├── InvalidUserDataException
│   │       ├── InvalidUserStateException
│   │       └── EmailNotVerifiedException
│   └── verification
│       ├── model
│       │   ├── EmailVerification
│       │   ├── VerificationTokenHash
│       │   └── VerificationPurpose
│       └── exception
│           └── InvalidEmailVerificationException
└── infrastructure
    ├── configuration
    │   └── IdentityConfiguration
    ├── persistence
    │   ├── adapter
    │   │   ├── JooqUserRepositoryAdapter
    │   │   ├── JooqEmailVerificationRepositoryAdapter
    │   │   └── JooqRefreshTokenRepositoryAdapter
    │   ├── exception
    │   │   └── InvalidPersistenceDataException
    │   ├── mapper
    │   │   ├── UserPersistenceMapper
    │   │   ├── EmailVerificationPersistenceMapper
    │   │   └── RefreshTokenPersistenceMapper
    │   └── data
    │       ├── model
    │       │   └── UserPersistenceData
    │       ├── repository
    │       │   ├── UserJooqRepository
    │       │   ├── EmailVerificationJooqRepository
    │       │   └── RefreshTokenJooqRepository
    │       └── generated
    ├── security
    │   ├── password
    │   │   └── BCryptPasswordHasher
    │   ├── token
    │   │   ├── SpringJwtAccessTokenIssuer
    │   │   ├── SecureRefreshTokenIssuer
    │   │   ├── Sha256RefreshTokenHasher
    │   │   ├── SecureVerificationTokenGenerator
    │   │   └── Sha256VerificationTokenHasher
    │   ├── authentication
    │   │   └── IdentityJwtAuthenticationConverter
    │   └── configuration
    │       ├── SecurityConfiguration
    │       ├── JwtConfiguration
    │       ├── IdentityPasswordProperties
    │       └── IdentityTokenProperties
    ├── messaging
    │   ├── email
    │   │   ├── NotificationVerificationEmailAdapter
    │   │   └── IdentityNotificationProperties
    │   └── event
    │       └── SpringIntegrationEventPublisher
    └── time
        └── SystemTimeProvider
```

Общий `io.github.edtechdevelopment.TimeConfiguration` находится в composition
root приложения, а не внутри бизнес-модуля. Он предоставляет один UTC `Clock`
для module-owned time adapters Identity и Notifications.

## Публичная граница Notifications, используемая Identity

```text
notifications
└── api                       @NamedInterface("api")
    ├── NotificationGateway
    ├── command               @NamedInterface("api")
    │   └── SendVerificationEmailCommand
    └── model                 @NamedInterface("api")
        └── VerificationEmailPurpose
```

Identity импортирует только этот named interface. Persistence, delivery worker,
SMTP и шаблоны остаются внутренними пакетами Notifications.

Реализованная внутренняя часть очереди:

```text
notifications
├── domain.delivery
│   ├── exception
│   └── model
├── application
│   ├── service
│   └── port.out
└── infrastructure
    ├── configuration
    │   └── NotificationsConfiguration
    ├── messaging.email
    │   └── SpringMailVerificationEmailSender
    ├── persistence
    │   ├── adapter
    │   ├── data.repository
    │   ├── data.generated
    │   └── mapper
    ├── scheduling
    │   └── VerificationEmailDeliveryScheduler
    └── time
        └── SystemTimeProvider
```

`NotificationsConfiguration` публикует `EnqueueVerificationEmailService` как
транзакционный Spring bean публичного типа `NotificationGateway` и связывает его
с собственными persistence/time adapters. Там же явно собираются Spring Mail
sender, processing service и условный scheduler. Локально sender подключён к
Mailpit; production использует внешний SMTP provider и внешние secrets.

В текущем реализованном срезе Presentation присутствуют registration-части
`AuthController`, `RegisterRequest`, `VerificationPendingResponse`,
`AuthPresentationMapper` и общий формат ошибок. Остальные элементы дерева
остаются целевой структурой следующих use cases.

## Ответственность верхних пакетов

| Пакет | Ответственность | Разрешённые зависимости |
|---|---|---|
| `identity.api` | Публичные запросы, DTO и события Identity | Простые Java-типы, без внутренних пакетов Identity |
| `identity.presentation` | HTTP, JSON, cookies, валидация запросов и представление ошибок | `application.port.in`, `application.command`, `application.query`, `application.result` |
| `identity.application` | Сценарии использования, транзакционные границы и порты | `domain`, `identity.api`; framework-аннотации только на композиционных границах |
| `identity.domain` | Бизнес-состояние, инварианты, поведение и доменные события | Только JDK |
| `identity.infrastructure` | Реализации портов, SQL, crypto/JWT, интеграции и конфигурация | `application.port.out`, `application.model`, `domain`, внешние библиотеки |

## Ответственность пакетов Application

| Пакет | Содержимое |
|---|---|
| `port.in.account` | Контракты регистрации, чтения и изменения текущего пользователя |
| `port.in.verification` | Контракты подтверждения и повторной отправки email |
| `port.in.authentication` | Контракты входа, обновления сессии и выхода |
| `port.out.persistence` | Абстракции хранения агрегатов и refresh tokens |
| `port.out.security` | Хеширование паролей и токенов, генерация и выпуск токенов |
| `port.out.messaging` | Отправка письма через Notifications и публикация интеграционных событий |
| `command.*` | Неизменяемые входные данные командных use cases |
| `query` | Неизменяемые входные данные запросов |
| `result` | Результаты use cases, безопасные для Presentation |
| `model` | Внутренние application-модели токенов и их состояния |
| `service.*` | Реализации use cases, разделённые по крупным функциональным областям |
| `mapper` | Преобразование Domain → application result/API DTO |
| `exception` | Ошибки сценариев использования |

## Ответственность пакетов Infrastructure

| Пакет | Содержимое |
|---|---|
| `persistence.adapter` | Реализации output repositories и координация mapper + data repository |
| `persistence.mapper` | Преобразование доменных и persistence-моделей |
| `persistence.data.repository` | Низкоуровневые SQL-операции через `DSLContext` |
| `persistence.data.generated` | Сгенерированные jOOQ table, record, schema, key и index types; ручное редактирование запрещено |
| `security.password` | BCrypt-реализация `PasswordHasher` |
| `security.token` | Выпуск JWT, генерация и SHA-256-хеширование opaque tokens |
| `security.authentication` | Преобразование claims JWT в Spring Security authentication |
| `security.configuration` | `SecurityFilterChain`, JWT beans и configuration properties |
| `messaging.email` | Адаптер публичного API Notifications и формирование confirmation URL |
| `messaging.event` | Публикация публичных событий Identity |
| `time` | Реализация времени через внедрённый `Clock` |
