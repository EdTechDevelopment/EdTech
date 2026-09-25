# Identity Persistence — jOOQ

```mermaid
flowchart TB
    subgraph APP[Application]
        UR[UserRepository]
        EVR[EmailVerificationRepository]
        RTR[RefreshTokenRepository]
    end

    subgraph ADAPTERS[Infrastructure: adapters]
        JA[JooqUserRepositoryAdapter]
        EA[JooqEmailVerificationRepositoryAdapter]
        RA[JooqRefreshTokenRepositoryAdapter]
    end

    subgraph MAPPERS[Infrastructure: mappers]
        UM[UserPersistenceMapper]
        EM[EmailVerificationPersistenceMapper]
        RM[RefreshTokenPersistenceMapper]
    end

    subgraph DATA[Infrastructure: data]
        UJR[UserJooqRepository]
        EJR[EmailVerificationJooqRepository]
        RJR[RefreshTokenJooqRepository]
        UPD[UserPersistenceData]
        GEN[jOOQ generated types]
    end

    DB[(PostgreSQL)]

    JA -. implements .-> UR
    EA -. implements .-> EVR
    RA -. implements .-> RTR

    JA --> UM
    JA --> UJR
    EA --> EM
    EA --> EJR
    RA --> RM
    RA --> RJR

    UJR --> UPD
    UJR --> GEN
    EJR --> GEN
    RJR --> GEN
    GEN --> DB
```

## Граница типов

```mermaid
flowchart LR
    DOMAIN[Domain / Application models]
    ADAPTER[Repository adapter]
    MAPPER[Persistence mapper]
    RECORD[jOOQ record]
    SQL[DSLContext / SQL]

    DOMAIN <--> ADAPTER
    ADAPTER <--> MAPPER
    MAPPER <--> RECORD
    ADAPTER --> SQL
```

jOOQ records завершают свой жизненный цикл внутри persistence. `UserJooqRepository` работает с `UserPersistenceData` и generated types и не знает агрегат `User`.

Подробная physical schema находится в [описании базы данных](../catalog/database.md).
