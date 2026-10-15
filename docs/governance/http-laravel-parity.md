# HTTP Laravel / Almasix parity ledger

Durable deepen tracker for `kona-http` (and middleware-linked session CSRF only when already in HTTP scope).  
**Do not edit** `modules/extract` or `modules/grind` from this workstream.

**Branch:** `feat/http-laravel-parity`  
**Status:** B1 ready for PR  
**Updated:** 2026-10-15

## Laws

- 100% line + branch coverage on touched production code.
- Java-callable public API for new surfaces.
- Docs under `apps/docs/.../http/` enriched concurrently (progressive detailed voice; no comparison essays).
- Never mutate GitHub rulesets.

---

## Request

| API | Status | Notes |
|-----|--------|-------|
| `input` / `all` / `form` / `query` | done | |
| `data()` typed JSON + multipart | done | |
| `validate` | done | |
| `route` | done | |
| `file` / `hasFile` / `allFiles` | done | |
| `expectsJson` / `old` / `session` / cookies | done | |
| `only` / `except` | **done** | |
| `boolean` / `booleanValue` (Java) | **done** | |
| `date` | **done** | |
| `bearerToken` | **done** | |
| `ip` / `userAgent` | **done** | |
| `merge` / `replace` | **done** | |
| `has` / `filled` / `missing` | **done** | |
| `whenHas` / `whenFilled` / `whenMissing` | **done** | |
| `safe` | **done** | after successful `validate` |

## Response

| API | Status | Notes |
|-----|--------|-------|
| `text` / `html` / `json` / `noContent` | done | |
| `redirect` / `back` | done | |
| `download` / `file` / `binary` | done | M23 |
| `cookie` / `with` / `withInput` / `withErrors` | done | |
| `withHeaders` | **done** | |
| `stream` | **deferred** | in-memory body; documented |
| fluent `header` / `withStatus` / `withBody` | done | |

## Middleware

| API | Status | Notes |
|-----|--------|-------|
| Pipeline `pipe` | done | |
| Route + group middleware | done | |
| Aliases + `web` / `api` stacks (`HttpMiddleware`) | done | |
| Trust proxies / hosts | done | |
| Terminable middleware | **done** | |
| Priority ordering | **done** | global kernel sort |
| Controller `$middleware` property | **deferred** | next batch if needed |

## Controllers

| API | Status | Notes |
|-----|--------|-------|
| Base `Controller` + `FormRequest` | done | |
| Invokable | done | |
| Resource / API resource | done | |
| `Controllers.action` DI via Application | done | |
| Middleware on controller | **deferred** | |

## Validation rules

### Implemented (baseline + B1)

`required`, `nullable`, `bail`, `sometimes`, `string`, `integer`, `numeric`, `boolean`, `email`, `url`, `array`, `list`, `min`, `max`, `size`, `between`, `in`, `not_in`, `confirmed`, `same`, `different`, `regex`, `not_regex`, `file`, `image`, `mimes`, `mimetypes`, `filled`, `present`, `accepted`, `declined`, `alpha`, `alpha_num`, `alpha_dash`, `ascii`, `uuid`, `json`, `ip`, `ipv4`, `ipv6`, `timezone`, `date`, `date_format`, `after`, `before`, `after_or_equal`, `before_or_equal`, `starts_with`, `ends_with`, `digits`, `digits_between`, `lowercase`, `uppercase`, `distinct`, `required_if`, `required_unless`, `required_with`, `required_without`, `required_with_all`, `required_without_all`, `exists`, `unique`

**Counts:** ~24 baseline → **+34 added in B1** → **~58 total**.  
**Still missing (common Laravel):** niche rules (`prohibited*`, `exclude*`, `accepted_if`, `declined_if`, `mac_address`, `ulid`, `current_password`, …) — track in later batches.

### DB-aware

| Rule | Status | Notes |
|------|--------|-------|
| `unique` / `exists` | **done (API)** | `ExistenceChecker` pluggable; Extract wiring later |

## Exception rendering

| Behavior | Status |
|----------|--------|
| ValidationException → JSON 422 when `expectsJson` | done |
| ValidationException → redirect back + flash | done |
| HttpException JSON vs HTML | done |
| report / render callbacks | done |

## Docs (`apps/docs`)

| Page | Status |
|------|--------|
| `http/request-response.mdx` | **updated** |
| `http/validation.mdx` | **updated** |
| `http/middleware.mdx` | **updated** |
| Sidebar / Basics IA | HTTP already under **The basics** (progressive IA) |

## PR batches

| Batch | Scope | PR |
|-------|-------|-----|
| B1 | Ledger + Request helpers + validation rules + Response.withHeaders + terminable/priority + docs + tests | TBD |

## Close criteria

B1 rows **done** or **deferred** with reason; CI green; automerge to `main`.
