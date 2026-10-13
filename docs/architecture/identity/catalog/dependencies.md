# Зависимости модуля Identity

Обозначения соответствуют UML/PlantUML-представлению:

```text
A ..|> B    A реализует интерфейс B
A --> B     A использует B
A *-- B     A владеет B как частью своего состояния
A ..> B     пакет A зависит от пакета B
письмо уже приходит, но backend ещё не умеет обра```

Блоки ниже можно копировать в описание диаграмм или использовать как чек-лист при создании связей в Visual Paradigm.

## Зависимости слоёв

```text
identity.presentation
    ..> identity.application.port.in
    ..> identity.application.command
    ..> identity.application.query
    ..> identity.application.result

identity.application
    ..> identity.domain
    ..> identity.api

identity.infrastructure.persistence
    ..> identity.application.port.out.persistence
    ..> identity.application.model
    ..> identity.domain

identity.infrastructure.security
    ..> identity.application.port.out.security
    ..> identity.application.model
    ..> identity.domain

identity.infrastructure.messaging.email
    ..> identity.application.port.out.messaging
    ..> notifications.api

identity.infrastructure.messaging.event
    ..> identity.application.port.out.messaging
    ..> identity.api.event

identity.infrastructure.time
    ..> identity.application.port.out.TimeProvider

identity.infrastructure.integration
    ..> identity.api
    ..> identity.application.port.in.account
```

Запрещённые направления:

```text
identity.domain               -X-> identity.application
identity.domain               -X-> identity.infrastructure
identity.application          -X-> identity.infrastructure
identity.presentation         -X-> identity.infrastructure
identity.api                  -X-> identity.domain
other.module                  -X-> identity.domain
other.module                  -X-> identity.application
other.module                  -X-> identity.infrastructure
identity                      -X-> notifications.internal
```

## Реализация входных портов

```text
application.service.account.RegisterUserService
    ..|> application.port.in.account.RegisterUserUseCase

application.service.account.AddUserRoleService
    ..|> application.port.in.account.AddUserRoleUseCase

infrastructure.integration.RegistrationGatewayAdapter
    ..|> api.IdentityRegistrationGateway
    --> application.port.in.account.RegisterUserUseCase

infrastructure.integration.RoleGatewayAdapter
    ..|> api.IdentityRoleGateway
    --> application.port.in.account.AddUserRoleUseCase

application.service.account.GetCurrentUserService [PLANNED]
    ..|> application.port.in.account.GetCurrentUserUseCase

application.service.account.UpdateCurrentUserService
    ..|> application.port.in.account.UpdateCurrentUserUseCase

application.service.verification.ConfirmEmailService
    ..|> application.port.in.verification.ConfirmEmailUseCase

application.service.verification.ResendEmailVerificationService
    ..|> application.port.in.verification.ResendEmailVerificationUseCase

application.service.authentication.LoginService
    ..|> application.port.in.authentication.LoginUseCase

application.service.authentication.RefreshTokenService
    ..|> application.port.in.authentication.RefreshTokenUseCase

application.service.authentication.LogoutService
    ..|> application.port.in.authentication.LogoutUseCase

application.service.account.IdentityQueryService
    ..|> api.IdentityQuery
```

## Реализация выходных портов

```text
infrastructure.persistence.adapter.JooqUserRepositoryAdapter
    ..|> application.port.out.persistence.UserRepository

infrastructure.persistence.adapter.JooqEmailVerificationRepositoryAdapter
    ..|> application.port.out.persistence.EmailVerificationRepository

infrastructure.persistence.adapter.JooqRefreshTokenRepositoryAdapter
    ..|> application.port.out.persistence.RefreshTokenRepository

infrastructure.security.password.BCryptPasswordHasher
    ..|> application.port.out.security.PasswordHasher

infrastructure.security.token.SpringJwtAccessTokenIssuer
    ..|> application.port.out.security.AccessTokenIssuer

infrastructure.security.token.SecureRefreshTokenIssuer
    ..|> application.port.out.security.RefreshTokenIssuer

infrastructure.security.token.Sha256RefreshTokenHasher
    ..|> application.port.out.security.RefreshTokenHasher

infrastructure.security.token.SecureVerificationTokenGenerator
    ..|> application.port.out.security.VerificationTokenGenerator

infrastructure.security.token.Sha256VerificationTokenHasher
    ..|> application.port.out.security.VerificationTokenHasher

infrastructure.messaging.email.NotificationVerificationEmailAdapter
    ..|> application.port.out.messaging.VerificationEmailSender

infrastructure.messaging.event.SpringIntegrationEventPublisher
    ..|> application.port.out.messaging.IntegrationEventPublisher

infrastructure.time.SystemTimeProvider
    ..|> application.port.out.TimeProvider
```

Каждый output port имеет ровно одну production-реализацию в Infrastructure. Тестовые fake/stub реализации не учитываются в этом ограничении.

## Presentation

```text
presentation.auth.controller.AuthController
    --> application.port.in.account.RegisterUserUseCase
    --> application.port.in.authentication.LoginUseCase
    --> application.port.in.authentication.RefreshTokenUseCase
    --> application.port.in.authentication.LogoutUseCase
    --> presentation.auth.mapper.AuthPresentationMapper
    --> presentation.auth.cookie.RefreshTokenCookieFactory

presentation.auth.mapper.AuthPresentationMapper
    --> application.command.account.RegisterUserCommand
    --> application.command.verification.ConfirmEmailCommand
    --> application.result.RegistrationResult
    --> application.result.AuthenticationResult

presentation.auth.model.request.RegisterRequest
    --> application.command.account.RegistrationRole

presentation.auth.controller.EmailVerificationController
    --> application.port.in.verification.ConfirmEmailUseCase
    --> application.port.in.verification.ResendEmailVerificationUseCase
    --> presentation.auth.mapper.AuthPresentationMapper
    --> presentation.auth.cookie.RefreshTokenCookieFactory

presentation.account.controller.CurrentUserController
    --> application.port.in.account.GetCurrentUserUseCase
    --> application.port.in.account.UpdateCurrentUserUseCase
    --> presentation.account.mapper.UserPresentationMapper

presentation.account.validation.ValidAccountUpdate
    --> presentation.account.validation.AccountUpdateValidator

presentation.error.handler.IdentityExceptionHandler
    --> presentation.error.model.ApiError
    --> presentation.error.model.FieldErrorResponse
    --> presentation.error.model.ErrorCode
    --> presentation.auth.cookie.RefreshTokenCookieFactory

presentation.error.handler.RestAuthenticationEntryPoint
    ..|> org.springframework.security.web.AuthenticationEntryPoint
    --> presentation.error.model.ApiError

presentation.error.handler.RestAccessDeniedHandler
    ..|> org.springframework.security.web.access.AccessDeniedHandler
    --> presentation.error.model.ApiError
```

Контроллеры не используют repositories, domain aggregates, jOOQ или infrastructure adapters.
На текущем этапе реализованы регистрация, confirm, resend, login, refresh с
ротацией refresh cookie и идемпотентный logout текущей session-family.

## Application services

```text
application.service.account.RegisterUserService
    --> application.port.out.persistence.UserRepository
    --> application.port.out.persistence.EmailVerificationRepository
    --> application.port.out.security.PasswordHasher
    --> application.port.out.security.VerificationTokenGenerator
    --> application.port.out.security.VerificationTokenHasher
    --> application.port.out.messaging.VerificationEmailSender
    --> application.port.out.messaging.IntegrationEventPublisher
    --> application.port.out.TimeProvider
    --> domain.user.model.User
    --> domain.verification.model.EmailVerification

application.service.account.GetCurrentUserService [PLANNED]
    --> application.port.out.persistence.UserRepository
    --> application.mapper.UserResultMapper

application.service.account.UpdateCurrentUserService
    --> application.port.out.persistence.UserRepository
    --> application.port.out.persistence.EmailVerificationRepository
    --> application.port.out.security.VerificationTokenGenerator
    --> application.port.out.security.VerificationTokenHasher
    --> application.port.out.messaging.VerificationEmailSender
    --> application.port.out.messaging.IntegrationEventPublisher
    --> application.port.out.TimeProvider
    --> application.mapper.UserResultMapper

application.service.account.IdentityQueryService
    --> application.port.out.persistence.UserRepository
    --> application.mapper.IdentityApiMapper

application.service.verification.ConfirmEmailService
    --> application.port.out.persistence.EmailVerificationRepository
    --> application.port.out.persistence.UserRepository
    --> application.port.out.persistence.RefreshTokenRepository
    --> application.port.out.security.VerificationTokenHasher
    --> application.port.out.security.AccessTokenIssuer
    --> application.port.out.security.RefreshTokenIssuer
    --> application.port.out.security.RefreshTokenHasher
    --> application.port.out.messaging.IntegrationEventPublisher
    --> application.port.out.TimeProvider
    --> application.mapper.IdentityApiMapper

application.service.verification.ResendEmailVerificationService
    --> application.port.out.persistence.UserRepository
    --> application.port.out.persistence.EmailVerificationRepository
    --> application.port.out.security.VerificationTokenGenerator
    --> application.port.out.security.VerificationTokenHasher
    --> application.port.out.messaging.VerificationEmailSender
    --> application.port.out.TimeProvider

application.service.authentication.LoginService
    --> application.port.out.persistence.UserRepository
    --> application.port.out.persistence.RefreshTokenRepository
    --> application.port.out.security.PasswordHasher
    --> application.port.out.security.AccessTokenIssuer
    --> application.port.out.security.RefreshTokenIssuer
    --> application.port.out.security.RefreshTokenHasher
    --> application.port.out.TimeProvider
    --> application.mapper.UserResultMapper

application.service.authentication.RefreshTokenService
    --> application.port.out.persistence.RefreshTokenRepository
    --> application.port.out.persistence.UserRepository
    --> application.port.out.security.RefreshTokenHasher
    --> application.port.out.security.RefreshTokenIssuer
    --> application.port.out.security.AccessTokenIssuer
    --> application.port.out.TimeProvider
    --> application.service.authentication.RefreshSessionFactory

application.service.authentication.LogoutService
    --> application.port.out.persistence.UserRepository
    --> application.port.out.persistence.RefreshTokenRepository
    --> application.port.out.security.RefreshTokenHasher
    --> application.port.out.TimeProvider
```

Между `service.account`, `service.verification` и `service.authentication` нет зависимостей. Каждый сервис координирует свой сценарий через общие порты и domain objects.

## Application mappers

```text
application.mapper.UserResultMapper
    --> domain.user.model.User
    --> application.result.CurrentUserResult

application.mapper.IdentityApiMapper
    --> domain.user.model.User
    --> domain.user.event.UserRegisteredDomainEvent
    --> domain.user.event.UserActivatedDomainEvent
    --> domain.user.event.UserAccountUpdatedDomainEvent
    --> api.model.UserSummary
    --> api.event.UserRegisteredEvent
    --> api.event.UserActivatedEvent
    --> api.event.UserAccountUpdatedEvent
```

## Domain

```text
domain.user.model.User
    *-- domain.user.model.Email
    *-- domain.user.model.PasswordHash
    *-- domain.user.model.UserRole
    *-- domain.user.model.UserStatus
    --> domain.user.event.UserRegisteredDomainEvent
    --> domain.user.event.UserActivatedDomainEvent
    --> domain.user.event.UserAccountUpdatedDomainEvent

domain.verification.model.EmailVerification
    *-- domain.user.model.Email
    *-- domain.verification.model.VerificationTokenHash
    *-- domain.verification.model.VerificationPurpose
```

Между двумя aggregate roots нет объектной ссылки:

```text
domain.verification.model.EmailVerification
    --> UUID userId
```

`EmailVerification` хранит identity пользователя как `UUID`, поэтому изменение одного агрегата не требует загрузки другого внутри domain-модели. Их совместное изменение координирует application service в транзакции.

## Persistence

```text
infrastructure.persistence.adapter.JooqUserRepositoryAdapter
    --> infrastructure.persistence.data.repository.UserJooqRepository
    --> infrastructure.persistence.mapper.UserPersistenceMapper

infrastructure.persistence.adapter.JooqEmailVerificationRepositoryAdapter
    --> infrastructure.persistence.data.repository.EmailVerificationJooqRepository
    --> infrastructure.persistence.mapper.EmailVerificationPersistenceMapper

infrastructure.persistence.adapter.JooqRefreshTokenRepositoryAdapter
    --> infrastructure.persistence.data.repository.RefreshTokenJooqRepository
    --> infrastructure.persistence.mapper.RefreshTokenPersistenceMapper

infrastructure.persistence.mapper.UserPersistenceMapper
    --> infrastructure.persistence.data.model.UserPersistenceData
    --> domain.user.model.User

infrastructure.persistence.mapper.EmailVerificationPersistenceMapper
    --> infrastructure.persistence.data.generated
    --> domain.verification.model.EmailVerification

infrastructure.persistence.mapper.RefreshTokenPersistenceMapper
    --> infrastructure.persistence.data.generated
    --> application.model.RefreshTokenState

infrastructure.persistence.data.repository.UserJooqRepository
    --> org.jooq.DSLContext
    --> infrastructure.persistence.data.generated
    --> infrastructure.persistence.data.model.UserPersistenceData

infrastructure.persistence.data.repository.EmailVerificationJooqRepository
    --> org.jooq.DSLContext
    --> infrastructure.persistence.data.generated

infrastructure.persistence.data.repository.RefreshTokenJooqRepository
    --> org.jooq.DSLContext
    --> infrastructure.persistence.data.generated
```

Классы `*.data.repository` не зависят от `identity.domain`. jOOQ records не возвращаются из `infrastructure.persistence`.

## Security

```text
infrastructure.security.password.BCryptPasswordHasher
    --> org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
    --> infrastructure.security.configuration.IdentityPasswordProperties

infrastructure.security.token.SpringJwtAccessTokenIssuer
    --> org.springframework.security.oauth2.jwt.JwtEncoder
    --> infrastructure.security.configuration.IdentityTokenProperties

infrastructure.security.token.SecureRefreshTokenIssuer
    --> java.security.SecureRandom
    --> infrastructure.security.configuration.IdentityTokenProperties

infrastructure.security.token.SecureVerificationTokenGenerator
    --> java.security.SecureRandom
    --> infrastructure.security.configuration.IdentityTokenProperties

infrastructure.security.token.Sha256VerificationTokenHasher
    --> java.security.MessageDigest

infrastructure.security.authentication.IdentityJwtAuthenticationConverter
    ..|> org.springframework.core.convert.converter.Converter
    --> org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken

infrastructure.security.configuration.SecurityConfiguration
    --> org.springframework.security.web.AuthenticationEntryPoint
    --> org.springframework.security.web.access.AccessDeniedHandler
    --> infrastructure.security.authentication.IdentityJwtAuthenticationConverter
    --> infrastructure.security.request.CookieCredentialOriginFilter
    --> infrastructure.security.configuration.IdentityCorsProperties

infrastructure.security.request.CookieCredentialOriginFilter
    --> org.springframework.security.web.access.AccessDeniedHandler
    --> infrastructure.security.configuration.IdentityCorsProperties

infrastructure.security.configuration.JwtConfiguration
    --> infrastructure.security.configuration.IdentityTokenProperties
    --> org.springframework.security.oauth2.jwt.JwtEncoder
    --> org.springframework.security.oauth2.jwt.JwtDecoder
```

`SecurityConfiguration` получает error handlers по стандартным Spring Security интерфейсам и не импортирует `RestAuthenticationEntryPoint` или `RestAccessDeniedHandler` как конкретные классы.

## Messaging и Time

```text
infrastructure.messaging.email.NotificationVerificationEmailAdapter
    --> notifications.api.NotificationGateway
    --> notifications.api.command.SendVerificationEmailCommand
    --> notifications.api.model.VerificationEmailPurpose
    --> infrastructure.messaging.email.IdentityNotificationProperties

infrastructure.messaging.event.SpringIntegrationEventPublisher
    --> org.springframework.context.ApplicationEventPublisher
    --> api.event

infrastructure.time.SystemTimeProvider
    --> java.time.Clock

io.github.edtechdevelopment.TimeConfiguration
    --> java.time.Clock
```

Notifications сохраняет запрос на доставку внутри своей транзакции/очереди и выполняет SMTP-отправку вне транзакции Identity.

```text
notifications.api.NotificationGateway
    <-- notifications.application.service.EnqueueVerificationEmailService
    --> notifications.application.port.out.TimeProvider
    --> notifications.application.port.out.persistence.VerificationEmailDeliveryRepository
    --> notifications.domain.delivery.model.VerificationEmailDelivery

notifications.application.service.ProcessVerificationEmailDeliveriesService
    --> notifications.application.port.out.TimeProvider
    --> notifications.application.port.out.email.VerificationEmailSender
    --> notifications.application.port.out.persistence.VerificationEmailDeliveryRepository
    --> notifications.domain.delivery.model.VerificationEmailDelivery
    --> org.springframework.transaction.support.TransactionOperations

notifications.infrastructure.messaging.email.SpringMailVerificationEmailSender
    --> notifications.application.port.out.email.VerificationEmailSender
    --> org.springframework.mail.javamail.JavaMailSender

notifications.infrastructure.scheduling.VerificationEmailDeliveryScheduler
    --> notifications.application.service.ProcessVerificationEmailDeliveriesService

notifications.infrastructure.persistence.adapter.JooqVerificationEmailDeliveryRepositoryAdapter
    --> notifications.application.port.out.persistence.VerificationEmailDeliveryRepository
    --> notifications.infrastructure.persistence.mapper.VerificationEmailDeliveryPersistenceMapper
    --> notifications.infrastructure.persistence.data.repository.VerificationEmailDeliveryJooqRepository

notifications.infrastructure.persistence.data.repository.VerificationEmailDeliveryJooqRepository
    --> org.jooq.DSLContext
    --> notifications.infrastructure.persistence.data.generated
```

Application Notifications не импортирует infrastructure, а low-level jOOQ
repository не импортирует domain. Identity по-прежнему видит только
`notifications.api`.

```text
identity.infrastructure.configuration.IdentityConfiguration
    --> identity.application.service.account.RegisterUserService
    --> identity.infrastructure.messaging.email.NotificationVerificationEmailAdapter
    --> notifications.api.NotificationGateway

notifications.infrastructure.configuration.NotificationsConfiguration
    --> notifications.application.service.EnqueueVerificationEmailService
    --> notifications.application.service.ProcessVerificationEmailDeliveriesService
    --> notifications.infrastructure.messaging.email.SpringMailVerificationEmailSender
    --> notifications.infrastructure.scheduling.VerificationEmailDeliveryScheduler
    --> notifications.application.port.out.persistence.VerificationEmailDeliveryRepository
    --> notifications.infrastructure.time.SystemTimeProvider
```

Application services не обнаруживаются через `@Service`: module-owned
Infrastructure configurations явно собирают их из портов и адаптеров. После
регистрации Spring создаёт transaction proxies для `RegisterUserUseCase` и
`NotificationGateway`. `ProcessVerificationEmailDeliveriesService`, Spring Mail
sender и условный scheduler также явно зарегистрированы в
`NotificationsConfiguration`; processing service управляет короткими
транзакциями через `TransactionOperations`, поэтому отдельный proxy ему не нужен.

## Проверка зависимостей

В реализации следует закрепить эти правила тестами ArchUnit и проверкой Spring Modulith:

```text
api не зависит от presentation/application/domain/infrastructure
domain не зависит от Spring, jOOQ и остальных слоёв
application не зависит от presentation/infrastructure
presentation не зависит от domain/infrastructure
data.repository не зависит от domain
service.account не зависит от service.verification/service.authentication
service.verification не зависит от service.account/service.authentication
service.authentication не зависит от service.account/service.verification
другие модули используют только identity.api
identity использует только notifications.api
```
