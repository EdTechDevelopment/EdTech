# Identity Application

## Входные порты и сервисы

```mermaid
flowchart TB
    subgraph ACCOUNT[account]
        RUC[RegisterUserUseCase]
        GUC[GetCurrentUserUseCase]
        UUC[UpdateCurrentUserUseCase]
        RS[RegisterUserService]
        GS[GetCurrentUserService]
        US[UpdateCurrentUserService]
        IQS[IdentityQueryService]
        RUC -. implemented by .-> RS
        GUC -. implemented by .-> GS
        UUC -. implemented by .-> US
    end

    subgraph VERIFICATION[verification]
        CUC[ConfirmEmailUseCase]
        SUC[ResendEmailVerificationUseCase]
        CS[ConfirmEmailService]
        SS[ResendEmailVerificationService]
        CUC -. implemented by .-> CS
        SUC -. implemented by .-> SS
    end

    subgraph AUTHENTICATION[authentication]
        LUC[LoginUseCase]
        RTUC[RefreshTokenUseCase]
        LOUC[LogoutUseCase]
        LS[LoginService]
        RTS[RefreshTokenService]
        LOS[LogoutService]
        LUC -. implemented by .-> LS
        RTUC -. implemented by .-> RTS
        LOUC -. implemented by .-> LOS
    end

    IQ[identity.api.query.IdentityQuery] -. implemented by .-> IQS
```

## Выходные порты

```mermaid
flowchart LR
    subgraph SERVICES[Application services]
        AS[account services]
        VS[verification services]
        AUS[authentication services]
    end

    subgraph PERSISTENCE[port.out.persistence]
        UR[UserRepository]
        EVR[EmailVerificationRepository]
        RTR[RefreshTokenRepository]
    end

    subgraph SECURITY[port.out.security]
        PH[PasswordHasher]
        VTG[VerificationTokenGenerator]
        VTH[VerificationTokenHasher]
        ATI[AccessTokenIssuer]
        RTI[RefreshTokenIssuer]
        RTH[RefreshTokenHasher]
    end

    subgraph MESSAGING[port.out.messaging]
        VES[VerificationEmailSender]
        IEP[IntegrationEventPublisher]
    end

    TP[TimeProvider]

    AS --> UR
    AS --> EVR
    AS --> PH
    AS --> VTG
    AS --> VTH
    AS --> VES
    AS --> IEP
    AS --> TP

    VS --> UR
    VS --> EVR
    VS --> RTR
    VS --> VTG
    VS --> VTH
    VS --> ATI
    VS --> RTI
    VS --> RTH
    VS --> VES
    VS --> IEP
    VS --> TP

    AUS --> UR
    AUS --> RTR
    AUS --> PH
    AUS --> ATI
    AUS --> RTI
    AUS --> RTH
    AUS --> TP
```

Группы application services не соединены между собой. Общие операции доступны через output ports, domain behavior и mapper classes.

## Поток данных use case

```mermaid
flowchart LR
    HTTP[Request DTO] --> MAP_IN[Presentation mapper]
    MAP_IN --> COMMAND[Command / Query]
    COMMAND --> PORT_IN[Input port]
    PORT_IN --> SERVICE[Application service]
    SERVICE --> DOMAIN[Domain aggregate]
    SERVICE --> PORT_OUT[Output port]
    SERVICE --> RESULT[Application result]
    RESULT --> MAP_OUT[Presentation mapper]
    MAP_OUT --> RESPONSE[Response DTO]
```
