# Cadence ↔ Eloquent / Database parity ledger

Living audit of `dev.kona.cadence` against Laravel Eloquent / query builder / database layer (and Almasix ORM where named). Prefer Almasix shape when both exist.

**Status:** `done` | `partial` | `missing`

| Meta | Value |
|------|--------|
| Last audit | 2026-10-16 (B6) |
| Last merged deepen | PR #87 (B6 Eloquent parity) |
| Active deepen | **none** — B1–B6 closed; next deepen is Security / HTTP / Queues / Support |

---

## H2 as scaffold default (decision)

**Why H2 is the `kona new` / sample default today**

1. **Zero install** — no local Postgres/MySQL daemon for `kona new` demos and CI unit tests.
2. **In-process JDBC** — `jdbc:h2:mem:…` and file URLs work with only the H2 JAR on the classpath.
3. **Identifier parity** — scaffold URLs include `DATABASE_TO_LOWER=TRUE` so quoted `"users"` matches unquoted `users` in hand-written SQL (H2 otherwise upper-cases unquoted names).
4. **`DB_CLOSE_DELAY=-1`** on mem URLs keeps the DB alive for the JVM process (pool reconnects).

**Decision:** **Keep H2 as the scaffold/default** for local demo and tests. Document PostgreSQL / MySQL as the **production** targets. Do **not** silently change `kona new` to require a server database.

Evidence: `modules/cli/.../config/Database.kt.txt`, `samples/hello-*/config/Database.*`, `TestDb.kt`. Guide: `apps/docs/.../cadence/database.mdx`.

---

## Supported databases / grammars (today)

| Engine | Query `Grammar` | Schema `SchemaGrammar` | Notes |
|--------|-----------------|------------------------|-------|
| **H2** | ANSI | `H2SchemaGrammar` | Scaffold + tests |
| **PostgreSQL** | ANSI | STANDARD / POSTGRES | Production-ready quoting |
| **MySQL / MariaDB** | MYSQL backticks | `MySqlSchemaGrammar` (`AUTO_INCREMENT`) | **done** (B6) |
| **SQLite** | ANSI | `SqliteSchemaGrammar` (`AUTOINCREMENT`) | **done** (B6) |
| **Other JDBC** | `Grammar.registerUrlPrefix` | `SchemaGrammar.registerProduct` | Extension path **done** (#65) |

---

## Database layer

| API | Status | Evidence |
|-----|--------|----------|
| Connections / `fromConfig` / `DB` / transactions | **done** | `Database.kt`, `DB.kt` |
| Grammar + SchemaGrammar registries | **done** | #65 |
| Multi-DB docs | **done** | `cadence/database.mdx` |
| MySQL / SQLite schema specialization | **done** | B6 `MySqlSchemaGrammar` / `SqliteSchemaGrammar` |

---

## Model CRUD & attributes

| API | Status | Notes |
|-----|--------|-------|
| Core CRUD / soft deletes / refresh / replicate / fill | **done** | — |
| Dirty tracking + `isClean(key)` + model increment | **done** | #65 |
| Events via `ModelDispatcher` | **done** | — |
| Observer classes / `observe` | **done** | B5 — `ModelObserver` + `observe` |
| `push` / `withoutTimestamps` / `unguard` | **done** | B6 |
| Attribute magic `getFooAttribute` / `setFooAttribute` | **done** | B6 reflective accessors + mutators + `setRawAttribute` |

---

## Queries

| API | Status | Notes |
|-----|--------|-------|
| where* / orWhere* / In / Null / Between / Column / Raw | **done** | ModelBuilder orWhereNull/In* #65 |
| ModelBuilder `orWhereRaw` / `groupBy` / `having` / `selectRaw` / `addSelect` / `distinct` | **done** | B2 |
| when / unless / tap / locks / cursor / lazy / chunkById | **done** | #65 |
| `whereDate` / `Day` / `Month` / `Year` / `Time` (+ `or…`) | **done** | B2 (`cast` / `cadence`) |
| `havingRaw` / `orHaving` / `orHavingRaw` | **done** | B2 |
| Global scopes | **done** | B3 — `Scope` + `addGlobalScope` / `withoutGlobalScope(s)` |
| `union` / `unionAll` / `crossJoin` | **done** | B6 |
| `pluck(column, key)` keyed | **done** | B6 QueryBuilder + ModelBuilder + CadenceCollection |
| `sole` | **done** | B6 (+ `MultipleRecordsFoundException`) |
| Native SQL upsert | **done** | B6 `ON CONFLICT` / `ON DUPLICATE KEY` + portable fallback |

---

## Relations

| API | Status | Notes |
|-----|--------|-------|
| Core + through + morphOne/Many/To | **done** | M15 |
| morphToMany / morphedByMany | **done** | B4 |
| MorphTo `whereHas` / existence | **done** | B6 MorphMap OR-expansion |
| withAvg / withMax / withMin (+ load*) | **done** | B6 |
| BelongsToMany firstOrCreate | **done** | B6 |

---

## Batch queue

| Batch | Scope | Status |
|-------|--------|--------|
| **B1** | Builder deepen + grammar registries + database docs | **done** (#65) |
| **B2** | `whereDate*`; ModelBuilder select/group/having/orWhereRaw mirrors; `havingRaw`/`orHaving` | **done** (#70) |
| **B3** | Global scopes | **done** (#71) |
| **B4** | morphToMany / morphedByMany | **done** (#73) |
| **B5** | Observer class registration | **done** (pre-rename) |
| **B6** | withAvg/Max/Min; push; sole; keyed pluck; union/crossJoin; MorphTo whereHas; BelongsToMany firstOrCreate; accessors; native upsert; MySQL/SQLite DDL | **done** (#87) |

---

## Docs

- Public: `apps/docs/.../cadence/database.mdx`, query-builder, models, relations
- Internal: this ledger
