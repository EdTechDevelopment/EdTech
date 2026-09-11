# Identity Infrastructure — Overview

Диаграмма показывает реализации выходных портов Application и внешние технологии.

```mermaid
flowchart LR
    subgraph ID[Identity]
        subgraph APP[Application]
            PERSIST_PORTS[Persistence ports]
            SECURITY_PORTS[Security ports]
            MESSAGE_PORTS[Messaging ports]
            TIME_PORT[TimeProvider]
        end

        subgraph INFRA[Infrastructure]
            PERSIST[Persistence adapters]
            SECURITY[Security adapters]
            MESSAGE[Messaging adapters]
            TIME[SystemTimeProvider]
        end
    end

    PERSIST -. implements .-> PERSIST_PORTS
    SECURITY -. implements .-> SECURITY_PORTS
    MESSAGE -. implements .-> MESSAGE_PORTS
    TIME -. implements .-> TIME_PORT

    PERSIST --> JOOQ[jOOQ / DSLContext]
    JOOQ --> PG[(PostgreSQL)]
    SECURITY --> SPRING_SECURITY[Spring Security]
    MESSAGE --> NOTIFICATIONS[notifications.api]
    MESSAGE --> SPRING_EVENTS[Spring ApplicationEventPublisher]
    TIME --> CLOCK[java.time.Clock]
```

```text
identity.infrastructure
├── persistence
│   ├── adapter
│   ├── mapper
│   └── data
├── security
│   ├── password
│   ├── token
│   ├── authentication
│   └── configuration
├── messaging
│   ├── email
│   └── event
└── time
```

Infrastructure знает интерфейсы Application и реализует их. Application не импортирует классы Infrastructure.
