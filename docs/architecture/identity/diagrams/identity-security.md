# Identity Security — JWT and Tokens

## Выпуск пары токенов

```mermaid
sequenceDiagram
    participant S as Application Service
    participant A as AccessTokenIssuer
    participant R as RefreshTokenIssuer
    participant H as RefreshTokenHasher
    participant DB as RefreshTokenRepository
    participant P as Presentation

    S->>A: issue(user)
    A-->>S: JWT + expiresAt
    S->>R: issue()
    R-->>S: raw refresh token + expiresAt
    S->>H: hash(raw refresh token)
    H-->>S: SHA-256 hash
    S->>DB: save(hash, familyId, expiresAt)
    S-->>P: AuthenticationResult
    P-->>P: access token in JSON
    P-->>P: refresh token in HttpOnly cookie
```

## Проверка access JWT

```mermaid
sequenceDiagram
    participant C as Client
    participant F as Spring Security Filter Chain
    participant D as JwtDecoder
    participant X as IdentityJwtAuthenticationConverter
    participant API as Protected Controller

    C->>F: Authorization: Bearer JWT
    F->>D: verify RS256 signature and exp
    D-->>F: verified Jwt
    F->>X: convert(jwt)
    X-->>F: JwtAuthenticationToken(sub, roles)
    F->>API: authenticated request
```

Проверка JWT не обращается к PostgreSQL на каждом запросе. Краткий срок жизни access token ограничивает время действия уже выданных claims.

## Ротация refresh token

```mermaid
sequenceDiagram
    participant C as Client
    participant S as RefreshTokenService
    participant H as RefreshTokenHasher
    participant DB as RefreshTokenRepository
    participant U as UserRepository
    participant I as RefreshTokenIssuer

    C->>S: raw token from cookie
    S->>H: hash(raw token)
    H-->>S: token hash
    S->>DB: findByTokenHash(hash)
    DB-->>S: preliminary token state or empty
    alt token is unknown
        S-->>C: INVALID_REFRESH_TOKEN
    else token owner is known
        S->>U: findByIdForUpdate(userId)
        S->>DB: findByTokenHashForUpdate(hash)
        DB-->>S: locked current token state or empty
        alt token is revoked (reuse)
            S->>DB: revokeFamily(userId, familyId, now)
            S-->>C: INVALID_REFRESH_TOKEN
        else token is expired
            S-->>C: INVALID_REFRESH_TOKEN
        else account is not ACTIVE
            S->>DB: revokeFamily(userId, familyId, now)
            S-->>C: FORBIDDEN
        else token and account are active
            S->>I: issue()
            I-->>S: new raw token
            S->>H: hash(new raw token)
            S->>DB: revoke(current token)
            S->>DB: save(new hash, same familyId and expiresAt)
            S-->>C: new access token + rotated cookie
        end
    end
```

Поиск, проверка состояния, отзыв старого token и сохранение нового выполняются в
одной application-транзакции. Предварительное чтение нужно узнать `userId`,
после чего блокировки всегда берутся в порядке `User -> RefreshToken`.
`FOR UPDATE` не изменяет строку само по себе: оно
удерживает row-level lock, чтобы два параллельных запроса не использовали один
активный token одновременно. Старые отозванные записи сохраняются до истечения
retention-периода, иначе повторное предъявление нельзя связать с `familyId`.

## JWT claims и cookie

```text
JWT algorithm: RS256
JWT claims: sub, roles, iss, aud, iat, exp
Signing private key: external configuration / secret storage
Verification public key: JwtDecoder configuration

Refresh cookie:
HttpOnly = true
Secure = true in production
SameSite = Lax
Path = /api/v1/auth
Max-Age = refresh token lifetime
```

CORS разрешает только настроенные frontend origins. Предполагается размещение frontend и backend в пределах одного site.
