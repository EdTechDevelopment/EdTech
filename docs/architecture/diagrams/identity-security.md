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
    F->>D: verify signature, iss, aud, exp
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
    participant I as RefreshTokenIssuer

    C->>S: raw token from cookie
    S->>H: hash(raw token)
    H-->>S: token hash
    S->>DB: findActiveByTokenHash(hash, now)
    DB-->>S: current token state
    S->>DB: revoke(current token)
    S->>I: issue()
    I-->>S: new raw token
    S->>H: hash(new raw token)
    S->>DB: save(new hash, same familyId)
    S-->>C: new access token + rotated cookie
```

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
