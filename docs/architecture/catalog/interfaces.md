# Каталог интерфейсов

Сигнатуры ниже являются архитектурным контрактом. Типы коллекций, `Optional`, `UUID`, `Instant` и URI можно уточнить при реализации, не меняя направление зависимостей и смысл операций.

## Публичный API Identity

### `identity.api.IdentityQuery`

Публичный синхронный API чтения Identity для других модулей. Не возвращает domain-модель.

```java
public interface IdentityQuery {
    Optional<UserSummary> findUserById(UUID userId);
}
```

Реализация: `identity.application.service.account.IdentityQueryService`.

## Входные порты Application

### `identity.application.port.in.account.RegisterUserUseCase`

```java
public interface RegisterUserUseCase {
    RegistrationResult register(RegisterUserCommand command);
}
```

Реализация: `identity.application.service.account.RegisterUserService`.

### `identity.application.port.in.account.GetCurrentUserUseCase`

```java
public interface GetCurrentUserUseCase {
    CurrentUserResult getCurrentUser(GetCurrentUserQuery query);
}
```

Реализация: `identity.application.service.account.GetCurrentUserService`.

### `identity.application.port.in.account.UpdateCurrentUserUseCase`

```java
public interface UpdateCurrentUserUseCase {
    CurrentUserResult updateCurrentUser(UpdateCurrentUserCommand command);
}
```

Реализация: `identity.application.service.account.UpdateCurrentUserService`.

### `identity.application.port.in.verification.ConfirmEmailUseCase`

Подтверждает email и сразу создаёт аутентифицированную сессию.

```java
public interface ConfirmEmailUseCase {
    AuthenticationResult confirmEmail(ConfirmEmailCommand command);
}
```

Реализация: `identity.application.service.verification.ConfirmEmailService`.

### `identity.application.port.in.verification.ResendEmailVerificationUseCase`

```java
public interface ResendEmailVerificationUseCase {
    ResendVerificationResult resendEmailVerification(
        ResendEmailVerificationCommand command
    );
}
```

Реализация: `identity.application.service.verification.ResendEmailVerificationService`.

### `identity.application.port.in.authentication.LoginUseCase`

```java
public interface LoginUseCase {
    AuthenticationResult login(LoginCommand command);
}
```

Реализация: `identity.application.service.authentication.LoginService`.

### `identity.application.port.in.authentication.RefreshTokenUseCase`

```java
public interface RefreshTokenUseCase {
    AuthenticationResult refresh(RefreshTokenCommand command);
}
```

Реализация: `identity.application.service.authentication.RefreshTokenService`.

### `identity.application.port.in.authentication.LogoutUseCase`

```java
public interface LogoutUseCase {
    void logout(LogoutCommand command);
}
```

Реализация: `identity.application.service.authentication.LogoutService`.

## Выходные порты Persistence

### `identity.application.port.out.persistence.UserRepository`

Работает только с агрегатом `User` и доменными value objects. SQL и jOOQ-типы в контракт не входят.

```java
public interface UserRepository {
    Optional<User> findById(UUID userId);
    Optional<User> findByEmail(Email email);
    boolean existsByEmail(Email email);
    User save(User user);
}
```

Реализация: `identity.infrastructure.persistence.adapter.JooqUserRepositoryAdapter`.

### `identity.application.port.out.persistence.EmailVerificationRepository`

```java
public interface EmailVerificationRepository {
    Optional<EmailVerification> findActiveByTokenHash(
        VerificationTokenHash tokenHash,
        Instant now
    );

    EmailVerification save(EmailVerification verification);

    void invalidateActiveForUser(
        UUID userId,
        VerificationPurpose purpose,
        Instant invalidatedAt
    );
}
```

Реализация: `identity.infrastructure.persistence.adapter.JooqEmailVerificationRepositoryAdapter`.

### `identity.application.port.out.persistence.RefreshTokenRepository`

Refresh token является application-моделью состояния сессии. Открытое значение токена в этот порт не передаётся.

```java
public interface RefreshTokenRepository {
    Optional<RefreshTokenState> findActiveByTokenHash(
        String tokenHash,
        Instant now
    );

    RefreshTokenState save(RefreshTokenState token);
    void revoke(UUID tokenId, Instant revokedAt);
    void revokeFamily(UUID familyId, Instant revokedAt);
}
```

Реализация: `identity.infrastructure.persistence.adapter.JooqRefreshTokenRepositoryAdapter`.

## Выходные порты Security

### `identity.application.port.out.security.PasswordHasher`

```java
public interface PasswordHasher {
    PasswordHash hash(String rawPassword);
    boolean matches(String rawPassword, PasswordHash passwordHash);
}
```

Реализация: `identity.infrastructure.security.password.BCryptPasswordHasher`.

### `identity.application.port.out.security.VerificationTokenGenerator`

```java
public interface VerificationTokenGenerator {
    String generate();
}
```

Реализация: `identity.infrastructure.security.token.SecureVerificationTokenGenerator`.

### `identity.application.port.out.security.VerificationTokenHasher`

```java
public interface VerificationTokenHasher {
    VerificationTokenHash hash(String rawToken);
}
```

Реализация: `identity.infrastructure.security.token.Sha256VerificationTokenHasher`.

### `identity.application.port.out.security.AccessTokenIssuer`

```java
public interface AccessTokenIssuer {
    IssuedAccessToken issue(User user);
}
```

Реализация: `identity.infrastructure.security.token.SpringJwtAccessTokenIssuer`.

### `identity.application.port.out.security.RefreshTokenIssuer`

```java
public interface RefreshTokenIssuer {
    IssuedRefreshToken issue();
}
```

Реализация: `identity.infrastructure.security.token.SecureRefreshTokenIssuer`.

### `identity.application.port.out.security.RefreshTokenHasher`

```java
public interface RefreshTokenHasher {
    String hash(String rawToken);
}
```

Реализация: `identity.infrastructure.security.token.Sha256RefreshTokenHasher`.

## Выходные порты Messaging и Time

### `identity.application.port.out.messaging.VerificationEmailSender`

Порт описывает намерение Identity отправить письмо. SMTP, шаблоны и повторные попытки остаются в Notifications.

```java
public interface VerificationEmailSender {
    void sendVerificationEmail(
        Email recipient,
        String rawToken,
        VerificationPurpose purpose,
        Instant expiresAt
    );
}
```

Реализация: `identity.infrastructure.messaging.email.NotificationVerificationEmailAdapter`.

### `identity.application.port.out.messaging.IntegrationEventPublisher`

```java
public interface IntegrationEventPublisher {
    void publish(Object integrationEvent);
}
```

Реализация: `identity.infrastructure.messaging.event.SpringIntegrationEventPublisher`.

При реализации вместо общего `Object` допустимо добавить перегруженные методы для публичных событий Identity. Доменные события наружу не публикуются напрямую.

### `identity.application.port.out.TimeProvider`

```java
public interface TimeProvider {
    Instant now();
}
```

Реализация: `identity.infrastructure.time.SystemTimeProvider`.

## Публичный API Notifications

### `notifications.api.NotificationGateway`

```java
public interface NotificationGateway {
    void enqueue(SendVerificationEmailCommand command);
}
```

Identity использует только этот интерфейс и публичные типы `notifications.api.command` / `notifications.api.model`.

## Контракты Spring Security

Следующие классы реализуют стандартные framework-интерфейсы:

```text
identity.presentation.error.handler.RestAuthenticationEntryPoint
    ..|> org.springframework.security.web.AuthenticationEntryPoint

identity.presentation.error.handler.RestAccessDeniedHandler
    ..|> org.springframework.security.web.access.AccessDeniedHandler

identity.infrastructure.security.authentication.IdentityJwtAuthenticationConverter
    ..|> Converter<Jwt, AbstractAuthenticationToken>
```

`SecurityConfiguration` принимает `AuthenticationEntryPoint` и `AccessDeniedHandler` по стандартным интерфейсам, поэтому Infrastructure не зависит от конкретных presentation-классов.

## Матрица реализаций портов

| Интерфейс | Реализация | Количество реализаций в production |
|---|---|---:|
| `IdentityQuery` | `IdentityQueryService` | 1 |
| `RegisterUserUseCase` | `RegisterUserService` | 1 |
| `GetCurrentUserUseCase` | `GetCurrentUserService` | 1 |
| `UpdateCurrentUserUseCase` | `UpdateCurrentUserService` | 1 |
| `ConfirmEmailUseCase` | `ConfirmEmailService` | 1 |
| `ResendEmailVerificationUseCase` | `ResendEmailVerificationService` | 1 |
| `LoginUseCase` | `LoginService` | 1 |
| `RefreshTokenUseCase` | `RefreshTokenService` | 1 |
| `LogoutUseCase` | `LogoutService` | 1 |
| `UserRepository` | `JooqUserRepositoryAdapter` | 1 |
| `EmailVerificationRepository` | `JooqEmailVerificationRepositoryAdapter` | 1 |
| `RefreshTokenRepository` | `JooqRefreshTokenRepositoryAdapter` | 1 |
| `PasswordHasher` | `BCryptPasswordHasher` | 1 |
| `AccessTokenIssuer` | `SpringJwtAccessTokenIssuer` | 1 |
| `RefreshTokenIssuer` | `SecureRefreshTokenIssuer` | 1 |
| `RefreshTokenHasher` | `Sha256RefreshTokenHasher` | 1 |
| `VerificationTokenGenerator` | `SecureVerificationTokenGenerator` | 1 |
| `VerificationTokenHasher` | `Sha256VerificationTokenHasher` | 1 |
| `VerificationEmailSender` | `NotificationVerificationEmailAdapter` | 1 |
| `IntegrationEventPublisher` | `SpringIntegrationEventPublisher` | 1 |
| `TimeProvider` | `SystemTimeProvider` | 1 |
