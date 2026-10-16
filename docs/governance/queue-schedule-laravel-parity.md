# Queues + Scheduling ↔ Laravel / Almasix parity ledger

Living audit of `kona-queue`, `kona-schedule`, and grind `queue:*` / `schedule:*` / `steam` against Laravel Queues + Task Scheduling (Almasix where named). Prefer Almasix shape when both exist; use Laravel as the named equivalent for gaps.

**Status legend:** `done` | `partial` | `missing` | `deferred`

**Ownership:** This deepen owns `modules/queue`, `modules/schedule`, grind queue/schedule commands, and Digging Deeper docs for Queues + Scheduling. Does **not** own Cadence, HTTP, auth, or filesystem (except tiny doc cross-links).

| Meta | Value |
|------|--------|
| Last audit | 2026-10-15 |
| Baseline on `main` | M7 queues; M8 scheduling; M16 durable queues; progressive Digging deeper IA (#62) |
| Active deepen | Batch 1 — delay, middleware, unique, chain, worker flags, schedule frequencies/mutex/work, docs |

---

## 1. Jobs & dispatch

| API | Status | Evidence |
|-----|--------|----------|
| `Job` / `ShouldQueue` / `JobContract` | **done** | `Job.kt` |
| `Bus.dispatch` + connection + queue name | **done** | `Bus.kt` |
| `Bus.dispatchSync` | **done** | `Bus.kt` |
| Delay / `later` on dispatch | **done** | `Bus.later` + `Queue.push(..., delaySeconds)` |
| Named queue on push | **done** | `Queue.push` |
| Job chains | **done** | `PendingChain` / `ChainedJob` |
| Job batches (`Bus::batch`) | **partial** | Light `PendingBatch` / `Batch` (ids only) |
| Serializable durable payload | **done** | M16 `jobBody` + `JobRegistry` |
| Java-callable jobs | **done** | `JavaDurableJobTest` / `JavaQueueDeepenTest` |

## 2. Workers (`grind queue:work` / steam)

| API | Status | Evidence |
|-----|--------|----------|
| Worker pop / retry / failed log | **done** | `Worker.kt` |
| `--connection` / `--queue` / `--max` | **done** | `QueueWorkCommand` |
| `--once` | **done** | `WorkerOptions.once` |
| `--stop-when-empty` / `--daemon` | **done** | `WorkerOptions` |
| `--timeout` (per-job) | **done** | `WorkerOptions.timeoutSeconds` |
| `--memory` | **done** | `WorkerOptions.memoryMb` (`0` = off) |
| `--tries` / `--backoff` overrides | **done** | `WorkerOptions` |
| `--sleep` + idle cycles | **done** | daemon loop |
| Backoff via `availableAt` | **done** | M16 |

## 3. Failed jobs

| API | Status | Evidence |
|-----|--------|----------|
| List / retry / flush | **done** | `queue:failed|retry|flush` |
| Memory / file / database failed drivers | **done** | M16 |
| Retry re-dispatches payload | **done** | M16 |

## 4. Drivers

| Driver | Status | Evidence |
|--------|--------|----------|
| sync | **done** | `SyncQueue` (delay ignored) |
| array / memory | **done** | `ArrayQueue` |
| redis | **done** | `RedisQueue` |
| database | **done** | `DatabaseQueue` |
| Push with delay (durable) | **done** | `push(..., delaySeconds)` |

## 5. Job middleware / unique / rate limit

| API | Status | Evidence |
|-----|--------|----------|
| `HasMiddleware` / pipeline | **done** | `JobPipeline` |
| `ShouldBeUnique` + lock | **done** | `UniqueLock` |
| Rate-limited job middleware | **done** | `RateLimited` + `JobRateLimiter` |
| `ReleaseJobException` | **done** | release without fail |

## 6. Task scheduling

| API | Status | Evidence |
|-----|--------|----------|
| Cron lists/ranges | **done** | `1,15` / `1-5` |
| Frequency helpers | **done** | five/ten/…/weekly/monthly |
| `withoutOverlapping` | **done** | `ScheduledEvent` + `ScheduleMutex` |
| `onOneServer` | **done** | file mutex |
| `runInBackground` | **done** | virtual thread |
| `grind schedule:run` / `list` | **done** | |
| `grind schedule:work` | **done** | loop with `--max` / `--sleep` |

## 7. Public docs

| Page | Status | Notes |
|------|--------|-------|
| Digging deeper → Queues | **done** | jobs / workers / failed |
| Digging deeper → Task scheduling | **done** | enriched |

## 8. Deferred

| Item | Why |
|------|-----|
| Full batch then/catch + DB batch repository | Later deepen |
| Horizon / metrics | Out of scope |
| Cron day-name tokens | Numeric DOW enough |
| Queue domain events | Optional later |

---

## Closure log

| Date | Batch | Notes |
|------|-------|-------|
| 2026-10-15 | audit | Ledger created |
| 2026-10-15 | batch1 | PR #69 — delay/later, middleware, unique, chain, light batch, worker flags, schedule frequencies/mutex/work, Digging deeper docs |
