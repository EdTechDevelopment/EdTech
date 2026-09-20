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

`NotificationVerificationEmailAdapter` формирует URL на основе
`IdentityNotificationProperties.frontendBaseUrl`. Пока реального frontend нет,
используется явно временный placeholder `http://frontend.example:3000` и путь
`/verify-email`; позже адрес заменяется только конфигурацией. `expiresAt` команды
равен deadline исходного verification token и позволяет Notifications не
отправлять истёкшую ссылку. HTML/text template, SMTP credentials, retry и
delivery status принадлежат Notifications.

Raw verification token не сохраняется в таблицах Identity. Реализованная delivery
queue хранит полный confirmation URL в `notification_email_deliveries` как
чувствительный payload до успешной отправки или `expiresAt`; такой payload нельзя
логировать. В конечном состоянии URL должен быть затёрт. Шифрование at rest
остаётся отдельным открытым усилением.

Для MVP очередь не хранит `attempt_count`, `available_at` и `lease_until`.
Временная ошибка будущего worker-а возвращает задание в `PENDING`, после чего оно
повторяется на следующем общем polling cycle. Зависший `PROCESSING` будет
определяться по `updated_at` и processing timeout. Конкретные worker/SMTP-параметры
зафиксированы как открытое решение `OPEN-018`.

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

`SpringIntegrationEventPublisher` передаёт тот же публичный Java event в
`ApplicationEventPublisher` без дополнительного mapping. Spring publication по
умолчанию синхронна и выполняется в памяти текущего процесса. Момент обработки
зафиксированных данных задаёт consumer через
`@TransactionalEventListener(phase = AFTER_COMMIT)`; это не делает обработку
асинхронной и не даёт durable delivery. Надёжный outbox остаётся отдельным
решением из `OPEN-011`.

## TimeProvider

```mermaid
flowchart LR
    CONFIG[Application TimeConfiguration]
    CLOCK[java.time.Clock]
    IDENTITY_SERVICE[Identity application service]
    IDENTITY_PORT[Identity TimeProvider]
    IDENTITY_ADAPTER[Identity SystemTimeProvider]
    NOTIFICATION_SERVICE[Notifications application service]
    NOTIFICATION_PORT[Notifications TimeProvider]
    NOTIFICATION_ADAPTER[Notifications SystemTimeProvider]

    CONFIG --> CLOCK
    IDENTITY_SERVICE --> IDENTITY_PORT
    IDENTITY_ADAPTER -. implements .-> IDENTITY_PORT
    IDENTITY_ADAPTER --> CLOCK
    NOTIFICATION_SERVICE --> NOTIFICATION_PORT
    NOTIFICATION_ADAPTER -. implements .-> NOTIFICATION_PORT
    NOTIFICATION_ADAPTER --> CLOCK
```

`Clock` создаётся в application composition root и внедряется через конструкторы.
Production использует один UTC system clock, а каждый модуль сохраняет собственный
application port и adapter. В unit-тестах используются fixed/mutable clock или
fake `TimeProvider`. Production adapters обрезают наносекунды `Instant` до
микросекунд: это точность PostgreSQL `TIMESTAMPTZ`, поэтому один `expiresAt`
остаётся буквально одинаковым в HTTP-ответе и обеих module-owned таблицах.
