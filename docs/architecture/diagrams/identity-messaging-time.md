# Identity Messaging and Time

## Verification email

```mermaid
sequenceDiagram
    participant S as Identity Application Service
    participant P as VerificationEmailSender
    participant A as NotificationVerificationEmailAdapter
    participant G as notifications.api.NotificationGateway
    participant N as Notifications
    participant SMTP as SMTP provider

    S->>P: sendVerificationEmail(email, rawToken, purpose, expiresAt)
    P->>A: output port call
    A->>A: build confirmation URL
    A->>G: enqueue(SendVerificationEmailCommand)
    G->>N: persist delivery request
    N-->>A: accepted
    Note over S,N: Identity transaction can complete
    N->>SMTP: send asynchronously
```

`NotificationVerificationEmailAdapter` формирует URL на основе `IdentityNotificationProperties.frontendBaseUrl`. HTML/text template, SMTP credentials, retry и delivery status принадлежат Notifications.

## Интеграционные события

```mermaid
flowchart LR
    DOMAIN[Domain event]
    MAPPER[IdentityApiMapper]
    PUBLIC[identity.api.event]
    PORT[IntegrationEventPublisher]
    ADAPTER[SpringIntegrationEventPublisher]
    SPRING[ApplicationEventPublisher]
    SUBSCRIBER[Other module subscriber]

    DOMAIN --> MAPPER
    MAPPER --> PUBLIC
    PUBLIC --> PORT
    PORT --> ADAPTER
    ADAPTER --> SPRING
    SPRING --> SUBSCRIBER
```

Внутренние подписчики, которым нужны зафиксированные данные, обрабатывают событие после commit транзакции. Публичное событие не содержит domain objects.

## TimeProvider

```mermaid
flowchart LR
    SERVICE[Application service]
    PORT[TimeProvider]
    ADAPTER[SystemTimeProvider]
    CLOCK[java.time.Clock]

    SERVICE --> PORT
    ADAPTER -. implements .-> PORT
    ADAPTER --> CLOCK
```

`Clock` внедряется через конструктор. Production использует UTC system clock, а тесты — fixed/mutable clock.
