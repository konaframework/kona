# Security ↔ Laravel / Almasix parity ledger

Living audit of `kona-auth` + `kona-session` against Laravel Auth / Session / Encryption / Hashing / Passwords / Sanctum-class tokens / Rate limiting / CSRF / CORS (and Almasix where named). Prefer Almasix shape when both exist; use Laravel as the named equivalent for gaps.

**Status legend:** `done` | `partial` | `missing`

**Ownership:** This deepen owns `modules/auth`, `modules/session` (session/CSRF only), Security docs, and `grind make:policy`. Does **not** own HTTP Request/Response/Validation, Cadence ORM internals, or other grind commands.

| Meta | Value |
|------|--------|
| Last audit | 2026-10-16 |
| Baseline on `main` | M3 (#9); M17 session scaffold; M21 form round-trip; docs IA #62; Batch 1 #66 |
| Active deepen | Batch 2 — CadenceUserProvider + durable stores + Argon2 |

---

## 1. Authentication

| API | Status | Evidence |
|-----|--------|----------|
| Guards / providers / `AuthManager` | **done** | `AuthManager`, `SessionGuard`, `TokenGuard` |
| `attempt` / `login` / `logout` / `check` / `guest` / `id` / `user` | **done** | `Guard` + `AuthManager` |
| Cookie remember + `viaRemember` | **done** | Batch 1 `SessionGuard` |
| `once` / `onceUsingId` / `loginUsingId` / `validate` | **done** | Batch 1 |
| `Auth` facade | **done** | Batch 1 |
| Middleware `Authenticate` / `RedirectIfAuthenticated` / `AuthenticateSession` | **done** | Batch 1 |
| Intended URL | **done** | `Authenticate.INTENDED_KEY` |
| Cadence `UserProvider` | **done** | Batch 2 `CadenceUserProvider` |

## 2. Authorization

| API | Status | Evidence |
|-----|--------|----------|
| `Gate.define` / `allows` / `denies` / `authorize` / `has` | **done** | `Gate.kt` |
| `Gate.any` / `none` / `before` | **done** | Batch 1 |
| Policy discovery | **done** | `Gate.discoverPolicy` |
| `Authorize` middleware | **done** | Batch 1 |
| Crema `@can` / `@cannot` | **done** | `CremaRuntime` |
| `grind make:policy` | **done** | Batch 1 |

## 3. Session / CSRF

| API | Status | Evidence |
|-----|--------|----------|
| Drivers + flash / regenerate / invalidate / pull / previous URL | **done** | `kona-session` |
| CSRF | **done** | `VerifyCsrfToken` |

## 4. Encryption / hashing / passwords

| API | Status | Evidence |
|-----|--------|----------|
| Encrypter + APP_KEY | **done** | `Encrypter` |
| Hash make/check/needsRehash | **done** | `Hash` / `BcryptHasher` |
| Argon2id hasher | **done** | Batch 2 `Argon2Hasher` (`argon2` / `argon2id`) |
| Password broker | **done** | `PasswordBroker` + `PasswordResetTokenRepository` |
| Durable password-reset DB store | **done** | Batch 2 `DatabasePasswordResetTokenRepository` |
| `PasswordRules` helper | **done** | Batch 1 |

## 5. Tokens / rate limit / headers

| API | Status | Evidence |
|-----|--------|----------|
| PAT + `can` / `cant` | **done** | Batch 1 `cant` |
| Durable PAT DB store | **done** | Batch 2 `DatabasePersonalAccessTokenRepository` |
| Throttle + named limiters | **done** | + `RateLimit.perMinute` factories |
| Security headers / CORS | **done** | Middleware |

## 6. Public docs

| Page | Status |
|------|--------|
| security/* guides | **done** Batch 2 authentication enrich |
| Sidebar Security group | **done** (#62) |

## Closed batches

| Batch | PR | Summary |
|-------|-----|---------|
| 1 | [#66](https://github.com/konaframework/kona/pull/66) | Auth facade; once/validate/loginUsingId/viaRemember; remember cookie; Authenticate / RedirectIfAuthenticated / AuthenticateSession / Authorize; Gate any/none/before/discoverPolicy; PasswordRules; RateLimit factories; make:policy; security docs enrich |
| 2 | shipping | CadenceUserProvider; PasswordResetTokenRepository + Database store; DatabasePersonalAccessTokenRepository; Argon2Hasher; config drivers; docs |

## Deferrals

- None remaining in Security deepen scope.
