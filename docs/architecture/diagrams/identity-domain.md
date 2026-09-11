# Identity Domain

## Агрегаты и value objects

```mermaid
classDiagram
    class User {
        <<aggregate root>>
        UUID id
        Email email
        Email pendingEmail
        PasswordHash passwordHash
        String firstName
        String lastName
        Set roles
        UserStatus status
        Instant emailVerifiedAt
        Instant createdAt
        Instant updatedAt
        verifyRegistrationEmail()
        requestEmailChange()
        confirmPendingEmail()
        updateProfile()
        changePassword()
        addRole()
        suspend()
        deactivate()
    }

    class Email {
        <<value object>>
        String value
    }

    class PasswordHash {
        <<value object>>
        String value
    }

    class UserRole {
        <<enumeration>>
        STUDENT
        TUTOR
        ADMIN
    }

    class UserStatus {
        <<enumeration>>
        PENDING_VERIFICATION
        ACTIVE
        SUSPENDED
        DEACTIVATED
    }

    class EmailVerification {
        <<aggregate root>>
        UUID id
        UUID userId
        Email targetEmail
        VerificationTokenHash tokenHash
        VerificationPurpose purpose
        Instant expiresAt
        Instant consumedAt
        Instant invalidatedAt
        Instant createdAt
        isActiveAt() boolean
        consume()
        invalidate()
    }

    class VerificationTokenHash {
        <<value object>>
        String value
    }

    class VerificationPurpose {
        <<enumeration>>
        REGISTRATION
        EMAIL_CHANGE
    }

    User *-- Email : current / pending
    User *-- PasswordHash
    User *-- UserRole
    User *-- UserStatus
    EmailVerification *-- Email : target
    EmailVerification *-- VerificationTokenHash
    EmailVerification *-- VerificationPurpose
    EmailVerification ..> User : UUID userId
```

Пунктирная связь `EmailVerification ..> User` означает ссылку по идентификатору. Один aggregate root не хранит другой aggregate root в своём объектном графе.

## Доменные события

```mermaid
classDiagram
    class User
    class UserRegisteredDomainEvent {
        UUID userId
        Email email
        Instant occurredAt
    }
    class UserActivatedDomainEvent {
        UUID userId
        Email email
        Instant occurredAt
    }
    class UserAccountUpdatedDomainEvent {
        UUID userId
        Set changedFields
        Instant occurredAt
    }

    User ..> UserRegisteredDomainEvent : creates
    User ..> UserActivatedDomainEvent : creates
    User ..> UserAccountUpdatedDomainEvent : creates
```

Domain events внутренние. `IdentityApiMapper` преобразует их в типы `identity.api.event` перед публикацией за границы модуля.
