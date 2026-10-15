# Extract ↔ Eloquent / Database parity ledger

Living audit of `dev.kona.extract` against Laravel Eloquent / query builder / database layer (and Almasix ORM where named). Prefer Almasix shape when both exist.

**Status:** `done` | `partial` | `missing`

Reconciled with explore audits ([Audit Extract model CRUD](8bd4dec2-3a9a-4899-ac32-3dc0c84550d5), [Audit Extract queries relations](35501d27-4e78-42ac-8662-f43c3873b906)) after **PR #65** landed — those audits predated B1; stale “missing” for tap/locks/cursor/`isClean(key)`/model increment are closed.

| Meta | Value |
|------|--------|
| Last audit | 2026-10-15 (post-#65 reconcile) |
| Last merged deepen | PR #65 (B1); B2 PR in flight |
| Active deepen | **B3** — global scopes (after B2 merges) |

---

## H2 as scaffold default (decision)

**Why H2 is the `kona new` / sample default today**

1. **Zero install** — no local Postgres/MySQL daemon for `kona new` demos and CI unit tests.
2. **In-process JDBC** — `jdbc:h2:mem:…` and file URLs work with only the H2 JAR on the classpath.
3. **Identifier parity** — scaffold URLs include `DATABASE_TO_LOWER=TRUE` so quoted `"users"` matches unquoted `users` in hand-written SQL (H2 otherwise upper-cases unquoted names).
4. **`DB_CLOSE_DELAY=-1`** on mem URLs keeps the DB alive for the JVM process (pool reconnects).

**Decision:** **Keep H2 as the scaffold/default** for local demo and tests. Document PostgreSQL / MySQL as the **production** targets. Do **not** silently change `kona new` to require a server database.

Evidence: `modules/cli/.../config/Database.kt.txt`, `samples/hello-*/config/Database.*`, `TestDb.kt`. Guide: `apps/docs/.../extract/database.mdx`.

---

## Supported databases / grammars (today)

| Engine | Query `Grammar` | Schema `SchemaGrammar` | Notes |
|--------|-----------------|------------------------|-------|
| **H2** | ANSI | `H2SchemaGrammar` | Scaffold + tests |
| **PostgreSQL** | ANSI | STANDARD / POSTGRES | Production-ready quoting |
| **MySQL / MariaDB** | MYSQL backticks | STANDARD + MySQL identifiers | AUTO_INCREMENT DDL still **partial** |
| **SQLite** | ANSI | STANDARD | No SQLite-specific DDL (**partial**) |
| **Other JDBC** | `Grammar.registerUrlPrefix` | `SchemaGrammar.registerProduct` | Extension path **done** (#65) |

---

## Database layer

| API | Status | Evidence |
|-----|--------|----------|
| Connections / `fromConfig` / `DB` / transactions | **done** | `Database.kt`, `DB.kt` |
| Grammar + SchemaGrammar registries | **done** | #65 |
| Multi-DB docs | **done** | `extract/database.mdx` |
| MySQL / SQLite schema specialization | **partial** | STANDARD types |

---

## Model CRUD & attributes

| API | Status | Notes |
|-----|--------|-------|
| Core CRUD / soft deletes / refresh / replicate / fill | **done** | — |
| Dirty tracking + `isClean(key)` + model increment | **done** | #65 |
| Events via `ModelDispatcher` | **done** | No Observer classes yet |
| Observer classes / `observe` | **missing** | B5 |
| `push` / `withoutTimestamps` / `unguard` | **missing** | B6 / later |
| Attribute magic `getFooAttribute` | **partial** | Hooks only |

---

## Queries

| API | Status | Notes |
|-----|--------|-------|
| where* / orWhere* / In / Null / Between / Column / Raw | **done** | ModelBuilder orWhereNull/In* #65 |
| ModelBuilder `orWhereRaw` / `groupBy` / `having` / `selectRaw` / `addSelect` / `distinct` | **done** | B2 |
| when / unless / tap / locks / cursor / lazy / chunkById | **done** | #65 |
| `whereDate` / `Day` / `Month` / `Year` / `Time` (+ `or…`) | **done** | B2 (`cast` / `extract`) |
| `havingRaw` / `orHaving` / `orHavingRaw` | **done** | B2 |
| Global scopes | **missing** | B3 |
| `union` / `crossJoin` | **missing** | later |
| `pluck(column, key)` keyed | **partial** | column list only |
| Native SQL upsert | **partial** | portable SELECT+UPDATE |

---

## Relations

| API | Status | Notes |
|-----|--------|-------|
| Core + through + morphOne/Many/To | **done** | M15 |
| morphToMany / morphedByMany | **missing** | B4 |
| MorphTo `whereHas` / existence | **missing** | correlate rejects MorphTo |
| withAvg / withMax / withMin | **missing** | B6 |
| BelongsToMany firstOrCreate | **missing** | HasOneOrMany only |

---

## Batch queue

| Batch | Scope | Status |
|-------|--------|--------|
| **B1** | Builder deepen + grammar registries + database docs | **done** (#65) |
| **B2** | `whereDate*`; ModelBuilder select/group/having/orWhereRaw mirrors; `havingRaw`/`orHaving` | **done** (PR) |
| **B3** | Global scopes | queued |
| **B4** | morphToMany / morphedByMany (+ MorphTo whereHas if in scope) | queued |
| **B5** | Observer class registration | queued |
| **B6** | withAvg/Max/Min; push; sole; keyed pluck | queued |

---

## Docs

- Public: `apps/docs/.../extract/database.mdx`, query-builder, models, relations
- Internal: this ledger
