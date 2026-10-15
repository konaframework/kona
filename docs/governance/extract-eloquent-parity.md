# Extract ↔ Eloquent / Database parity ledger

Living audit of `dev.kona.extract` against Laravel Eloquent / query builder / database layer (and Almasix ORM where named). Prefer Almasix shape when both exist.

**Status:** `done` | `partial` | `missing`

| Meta | Value |
|------|--------|
| Last audit | 2026-10-15 |
| Last code on `main` (pre-batch) | PR #61 |
| Active deepen | B1 in PR #65; next B2 (`whereDate*`, MySQL schema specialization) |

---

## H2 as scaffold default (decision)

**Why H2 is the `kona new` / sample default today**

1. **Zero install** — no local Postgres/MySQL daemon for `kona new` demos and CI unit tests.
2. **In-process JDBC** — `jdbc:h2:mem:…` and file URLs work with only the H2 JAR on the classpath.
3. **Identifier parity** — scaffold URLs include `DATABASE_TO_LOWER=TRUE` so quoted `"users"` matches unquoted `users` in hand-written SQL (H2 otherwise upper-cases unquoted names).
4. **`DB_CLOSE_DELAY=-1`** on mem URLs keeps the DB alive for the JVM process (pool reconnects).

**Decision:** **Keep H2 as the scaffold/default** for local demo and tests. Document PostgreSQL / MySQL as the **production** targets and make switching a first-class guide (`.env` + driver dependency). Do **not** silently change `kona new` to require a server database.

Evidence: `modules/cli/.../config/Database.kt.txt`, `samples/hello-*/config/Database.*`, `TestDb.kt`.

---

## Supported databases / grammars (today)

| Engine | Query `Grammar` | Schema `SchemaGrammar` | Evidence | Notes |
|--------|-----------------|------------------------|----------|-------|
| **H2** | `Grammar.ANSI` via `forUrl` | `H2SchemaGrammar` (`TEXT` → `CHARACTER VARYING`) | `Grammar.kt`, `SchemaGrammar.kt`, `SchemaTest` | Scaffold + tests default |
| **PostgreSQL** | `Grammar.ANSI` | `STANDARD` / `POSTGRES` alias | `forProduct("PostgreSQL")` → STANDARD | Production-ready quoting; DDL uses standard SQL |
| **MySQL / MariaDB** | `Grammar.MYSQL` (backticks) | `STANDARD` + MYSQL identifiers | `Grammar.forUrl("jdbc:mysql:")` | No MySQL-specific ID/`AUTO_INCREMENT` grammar yet (**partial**) |
| **SQLite** | `Grammar.ANSI` (URL not special-cased) | `STANDARD` | Same path as Postgres | Identity / type quirks not specialized (**partial**) |
| **Other JDBC** | ANSI unless registered | STANDARD unless registered | `Grammar.registerUrlPrefix`, `SchemaGrammar.registerProduct` | Extension path (**done** in B1) |

---

## Database layer

| API | Status | Evidence |
|-----|--------|----------|
| `Database` named connections / pool / `close` / `disconnect` | **done** | `Database.kt` |
| `Database.fromConfig` / `fromMap` / `DB_*` fallback | **done** | `Database.fromConfig`; env when `DB_URL` set |
| `DB` facade (table/select/insert/update/delete/statement/transaction) | **done** | `DB.kt` |
| Transactions (thread-bound connection) | **done** | `DatabaseConnection.transaction` |
| `ConnectionConfig` map + env keys | **done** | `url`, `username`, `password`, `driver`, `pool_size`, `quote_identifiers` |
| Custom `DataSource` + explicit `Grammar` | **done** | `addConnection(name, dataSource, grammar)` |
| Query `Grammar` URL registry | **done** | `Grammar.registerUrlPrefix` (B1) |
| Schema product registry | **done** | `SchemaGrammar.registerProduct` (B1) |
| MySQL-specific DDL (AUTO_INCREMENT, etc.) | **partial** | Uses STANDARD types |
| SQLite-specific DDL | **partial** | Uses STANDARD |
| Multi-DB docs (configure each + add new) | **done** | `apps/docs/.../extract/database.mdx` (B1) |

---

## Model CRUD & attributes

| API | Status | Evidence |
|-----|--------|----------|
| create/save/update/delete/soft/force/restore | **done** | `Extract`, `SoftDeletes` |
| refresh/fresh/replicate/fill/forceFill | **done** | `Extract`; `EloquentParityTest` |
| wasChanged/getChanges/isDirty/getDirty/getOriginal | **done** | `Extract` |
| `isClean()` / `isClean(key)` | **done** | B1 |
| touch/timestamps/custom PK/connectionName | **done** | `Extract` |
| Model `increment` / `decrement` | **done** | B1 |
| upsert/updateOrInsert/insertOrIgnore | **done** | QB + ModelBuilder |
| firstOrNew/firstOrCreate/updateOrCreate/findOrNew | **done** | Companion + builder; findOrNew B1 |
| truncate/destroy | **done** | #61 |
| Casts / CastsAttributes / mutator hooks | **done** / **partial** | hooks; no magic `getFooAttribute` reflection |
| appends/hidden/visible/toArray/toJson | **done** | `Extract` |
| Model events via `ModelDispatcher` | **done** | `ModelEvent.kt` |
| Observer classes | **missing** | — |
| `push` (save + relations) | **missing** | — |

---

## Queries

| API | Status | Evidence |
|-----|--------|----------|
| where*/orWhere*/In/Null/Between/Column/Raw | **done** | QB; ModelBuilder orWhereNull/In* **done** B1 |
| when/unless/tap | **done** | tap B1 |
| Local `scope { }` | **done** | ModelBuilder |
| Global scopes | **missing** | — |
| Soft-delete scope | **done** | ModelBuilder.toBase |
| order/limit/offset/chunk | **done** | — |
| cursor/lazy/lazyById/chunkById | **done** | B1 |
| aggregates/pluck/value/exists | **done** | — |
| lockForUpdate/sharedLock | **done** | B1 (sharedLock SQL may fail on H2) |
| paginate/simple/cursor | **done** | M15 |
| whereDate*/havingRaw/union | **missing** | — |

---

## Relations

| API | Status | Evidence |
|-----|--------|----------|
| hasOne/hasMany/belongsTo/belongsToMany | **done** | — |
| has*Through / morphOne/Many/To | **done** | M15 |
| morphToMany / morphedByMany | **missing** | — |
| with/load/loadMissing/withCount/Exists/Sum | **done** | — |
| whereHas* / pivot attach/sync/… | **done** | — |
| relation firstOrCreate/updateOrCreate | **done** | #61 |
| withAvg/Max/Min | **missing** | — |

---

## Batch queue

| Batch | Scope | Status |
|-------|--------|--------|
| **B1** | ORM: orWhere*, tap, locks, cursor/lazy/chunkById, findOrNew, isClean(key), increment; DB: Grammar/SchemaGrammar registries; docs: database.mdx + ledger | **done** (this PR) |
| **B2** | whereDate*; havingRaw; MySQL schema specialization | queued |
| **B3** | Global scopes | queued |
| **B4** | morphToMany / morphedByMany | queued |
| **B5** | Observer classes | queued |
| **B6** | withAvg/Max/Min; push; sole | queued |

---

## Docs

- Public: `apps/docs/src/content/docs/docs/extract/database.mdx` (connections, env, engines, adding a DB)
- Public: extract overview / query-builder / models (keep in sync)
- Internal: this ledger
