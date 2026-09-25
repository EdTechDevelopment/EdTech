# Identity — Layers and Dependencies

```mermaid
flowchart TB
    CLIENT[Web / Mobile client]
    OTHER[Other business modules]

    subgraph IDENTITY[identity]
        API[api<br/>public contract]
        PRESENTATION[presentation<br/>HTTP and security errors]
        APPLICATION[application<br/>use cases and ports]
        DOMAIN[domain<br/>aggregates and business rules]
        INFRASTRUCTURE[infrastructure<br/>adapters and configuration]
    end

    CLIENT --> PRESENTATION
    OTHER --> API
    PRESENTATION --> APPLICATION
    APPLICATION --> DOMAIN
    APPLICATION --> API
    INFRASTRUCTURE -. implements output ports .-> APPLICATION

    INFRASTRUCTURE --> PG[(PostgreSQL)]
    INFRASTRUCTURE --> NOTIFICATIONS[notifications.api]
    INFRASTRUCTURE --> SECURITY[Spring Security / crypto]

    API -. events .-> OTHER
```

## Правила

```text
PUBLIC:
  identity.api

INTERNAL:
  identity.presentation
  identity.application
  identity.domain
  identity.infrastructure

ALLOWED:
  presentation → application
  application → domain
  application → api
  infrastructure → application output ports
  infrastructure → domain/application models where adapter mapping requires them
  other modules → identity.api
  identity.messaging.email → notifications.api

FORBIDDEN:
  domain → Spring/jOOQ/application/infrastructure
  application → presentation/infrastructure
  presentation → infrastructure
  other modules → identity internal packages
  identity → notifications internal packages
```

Dependency Injection связывает input ports с application services и output ports с infrastructure adapters в runtime. Направление compile-time зависимости остаётся направленным к внутренним правилам и контрактам.
