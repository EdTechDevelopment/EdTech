# Каталог интерфейсов

Сигнатуры ниже являются архитектурным контрактом. Типы коллекций, `Optional`, `UUID`, `Instant` и URI можно уточнить при реализации, не меняя направление зависимостей и смысл операций.

## Публичный API Identity

### `identity.api.query.IdentityQuery`

Публичный синхронный API чтения Identity для других модулей. Не возвращает domain-модель.

```java
public interface IdentityQuery {
    Optional<UserSummary> findUserById(UUID userId);
}
```

Реализация: `identity.application.service.account.IdentityQueryService`.

На текущем этапе реализован только `findUserById(UUID)`. Batch-чтение и поиск по
подтверждённому email остаются частью целевой архитектуры и добавляются по
реальным use case модуля Tutoring.

## Входные порты Application

### `identity.application.port.in.account.RegisterUserUseCase`

```java
public interface RegisterUserUseCase {
    RegistrationResult register(RegisterUserCommand command);
}
```

Реализация: `identity.application.service.account.RegisterUserService`.

### `identity.application.port.in.account.GetCurrentUserUseCase`

Статус: `PLANNED`. Интерфейс и его реализация пока отсутствуют в коде и будут
добавлены вместе с составным `GET /me` после появления Tutoring.

```java
public interface GetCurrentUserUseCase {
    CurrentUserResult getCurrentUser(GetCurrentUserQuery query);
}
```

Планируемая реализация: `identity.application.service.account.GetCurrentUserService`.

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

Сценарий идемпотентен: отсутствующий или неизвестный token является успешным
no-op. Для известного token сервис соблюдает lock order `User → RefreshToken` и
отзывает всю его family, не затрагивая другие login-family пользователя.

Реализация: `identity.application.service.authentication.LogoutService`.

## Выходные порты Persistence

### `identity.application.port.out.persistence.UserRepository`

Работает только с агрегатом `User` и доменными value objects. SQL и jOOQ-типы в контракт не входят.

```java
public interface UserRepository {
    Optional<User> findById(UUID userId);
    Optional<User> findByIdForUpdate(UUID userId);
    Optional<User> findByEmail(Email email);
    Optional<User> findByCurrentOrPendingEmail(Email email);
    boolean existsByEmail(Email email);
    void save(User user);
}
```

`findByEmail` используется для аутентификации и ищет пользователя только по email
с `kind = CURRENT`. `findByCurrentOrPendingEmail` используется verification flow
и ищет оба kinds, не меняя семантику login. `existsByEmail` также проверяет оба
вида (`CURRENT` и `PENDING`), потому что pending email уже глобально зарезервирован.

`findByIdForUpdate` используется use case-ами, которые изменяют существующего
пользователя. Реализация выполняет `SELECT ... FOR UPDATE` основной строки до
чтения email и roles. Метод вызывается только внутри транзакции application service;
`MANDATORY` propagation запрещает освободить lock сразу после SELECT.

Реализация: `identity.infrastructure.persistence.adapter.JooqUserRepositoryAdapter`.

### `identity.application.port.out.persistence.EmailVerificationRepository`

```java
public interface EmailVerificationRepository {
    Optional<EmailVerification> findActiveByTokenHash(
        VerificationTokenHash tokenHash,
        Instant now
    );

    Optional<EmailVerification> findActiveByTokenHashForUpdate(
        VerificationTokenHash tokenHash,
        Instant now
    );

    void save(EmailVerification verification);

    void invalidateActiveForUser(
        UUID userId,
        VerificationPurpose purpose,
        Instant invalidatedAt
    );
}
```

`findActiveByTokenHash` выполняет предварительное неблокирующее чтение, чтобы
confirm узнал `userId`. После блокировки User метод
`findActiveByTokenHashForUpdate` повторно загружает только активную verification
и удерживает row-level lock до завершения транзакции. Locking read, save и
массовая инвалидизация требуют уже открытую транзакцию application service через
`Propagation.MANDATORY`.

Реализация: `identity.infrastructure.persistence.adapter.JooqEmailVerificationRepositoryAdapter`.

### `identity.application.port.out.persistence.RefreshTokenRepository`

Refresh token является application-моделью состояния сессии. Открытое значение токена в этот порт не передаётся.

```java
public interface RefreshTokenRepository {
    Optional<RefreshTokenState> findByTokenHash(
        String tokenHash
    );

    Optional<RefreshTokenState> findByTokenHashForUpdate(
        String tokenHash
    );

    void save(RefreshTokenState token);
    void revoke(UUID tokenId, Instant revokedAt);
    void revokeFamily(
        UUID userId,
        UUID familyId,
        Instant revokedAt
    );
}
```

`findByTokenHash` выполняет предварительное неблокирующее чтение только для
определения владельца. Затем `RefreshTokenService` блокирует User и повторно
читает запись через `findByTokenHashForUpdate`, которая удерживает row lock до
конца application-транзакции. Проверки `expiresAt` и `revokedAt` выполняет сервис:
это позволяет отличить неизвестный token от reuse и отозвать его token family.

Семья отзывается в границах конкретного пользователя. Пара `userId` и `familyId`
соответствует составному индексу `(user_id, family_id)` и не допускает глобальную
операцию над token family без проверки её владельца.

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
    IssuedAccessToken issue(User user, Instant issuedAt);
}
```

`issuedAt` задаётся application service через `TimeProvider`; порт не обращается
к системным часам самостоятельно. Реализация:
`identity.infrastructure.security.token.SpringJwtAccessTokenIssuer`.

### `identity.application.port.out.security.RefreshTokenIssuer`

```java
public interface RefreshTokenIssuer {
    IssuedRefreshToken issue(Instant expiresAt);
}
```

`expiresAt` вычисляет application service из фиксированной границы жизни token
family. Поэтому ротация не может незаметно продлить family ещё на 30 дней.
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
    void publish(UserRegisteredEvent event);
    void publish(UserActivatedEvent event);
    void publish(UserAccountUpdatedEvent event);
}
```

Реализация: `identity.infrastructure.messaging.event.SpringIntegrationEventPublisher`.

Типизированные перегрузки разрешают публиковать только публичные integration events
Identity и дают compile-time защиту от случайной публикации domain-объекта или
произвольного значения. Доменные события наружу не публикуются напрямую.

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
Пакеты `notifications.api`, `notifications.api.command` и
`notifications.api.model` явно объявлены частями одного Spring Modulith
`@NamedInterface("api")`. Явное объявление всех трёх пакетов не оставляет
вложенные command/model типы внутренними деталями модуля.

## Внутренние output-порты Notifications

### `notifications.application.port.out.TimeProvider`

```java
public interface TimeProvider {
    Instant now();
}
```

Реализация: `notifications.infrastructure.time.SystemTimeProvider`.

### `notifications.application.port.out.persistence.VerificationEmailDeliveryRepository`

```java
public interface VerificationEmailDeliveryRepository {
    void save(VerificationEmailDelivery delivery);
    List<VerificationEmailDelivery> findPending(Instant now, int limit);
    List<VerificationEmailDelivery> findStaleProcessing(
        Instant staleBefore,
        Instant now,
        int limit
    );
    List<VerificationEmailDelivery> findExpired(Instant now, int limit);
    void update(VerificationEmailDelivery delivery);
}
```

Реализация: `JooqVerificationEmailDeliveryRepositoryAdapter`.

### `notifications.application.port.out.email.VerificationEmailSender`

```java
public interface VerificationEmailSender {
    void send(VerificationEmailMessage message);
}
```

Production-реализация: `SpringMailVerificationEmailSender`. Локально её
`JavaMailSender` подключён к Mailpit, а в production — к внешнему
SMTP-провайдеру через deployment configuration и secrets.

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
| `GetCurrentUserUseCase` | `GetCurrentUserService` | 0 — отложен до полного `GET /me` |
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
| `notifications VerificationEmailDeliveryRepository` | `JooqVerificationEmailDeliveryRepositoryAdapter` | 1 |
| `notifications VerificationEmailSender` | `SpringMailVerificationEmailSender` | 1 |
