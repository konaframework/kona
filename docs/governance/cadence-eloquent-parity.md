# Cadence ↔ Eloquent / Database parity ledger

Living audit of `org.konaframework.cadence` against Laravel Eloquent / query builder / database layer (and Almasix ORM where named). Prefer Almasix shape when both exist.

**Status:** `done` | `partial` | `missing`

| Meta | Value |
|------|--------|
| Last audit | 2026-10-19 (M25 complete) |
| Last merged deepen | PR #87 (B6 Eloquent parity) |
| Active deepen | **none** — M25 first-class database engines exhausted (awaiting merge) |

---

## H2 as scaffold default (decision)

**Why H2 is the `kona new` / sample default today**

1. **Zero install** — no local Postgres/MySQL daemon for `kona new` demos and CI unit tests.
2. **In-process JDBC** — `jdbc:h2:mem:…` and file URLs work with only the H2 JAR on the classpath.
3. **Identifier parity** — scaffold URLs include `DATABASE_TO_LOWER=TRUE` so quoted `"users"` matches unquoted `users` in hand-written SQL (H2 otherwise upper-cases unquoted names).
4. **`DB_CLOSE_DELAY=-1`** on mem URLs keeps the DB alive for the JVM process (pool reconnects).

**Decision:** **Keep H2 as the scaffold/default** for local demo and tests. Document PostgreSQL / MySQL as the **production** targets. Do **not** silently change `kona new` to require a server database.

**Classpath fix:** `kona new` skeletons include `runtimeOnly("com.h2database:h2:2.3.232")`. Optional `kona new --database=<engine>` selects a named connection and adds that engine’s JDBC driver.

Evidence: `modules/cli/.../config/Database.kt.txt`, skeletons, `DatabaseEngines`, samples. Guide: `apps/docs/.../cadence/database.mdx`.

---

## Supported databases / grammars (M25)

| Engine | Query `Grammar` | Schema `SchemaGrammar` | Notes |
|--------|-----------------|------------------------|-------|
| **H2** | `Grammar.H2` | `H2SchemaGrammar` | Scaffold + tests; upsert `ON CONFLICT` |
| **PostgreSQL** | `Grammar.POSTGRES` | `PostgresSchemaGrammar` | Identity, JSONB |
| **MySQL** | `Grammar.MYSQL` | `MySqlSchemaGrammar` | `AUTO_INCREMENT`, backticks |
| **MariaDB** | `Grammar.MARIADB` | `MariaDbSchemaGrammar` | Distinct product; shared MySQL DDL family |
| **SQLite** | `Grammar.SQLITE` | `SqliteSchemaGrammar` | `AUTOINCREMENT`; documented alter limits |
| **Oracle** | `Grammar.ORACLE` | `OracleSchemaGrammar` | `VARCHAR2`, MERGE upsert, identity |
| **SQL Server** | `Grammar.SQLSERVER` | `SqlServerSchemaGrammar` | Brackets, `OFFSET/FETCH`, MERGE |
| **Other JDBC** | `Grammar.registerUrlPrefix` | `SchemaGrammar.registerProduct` | Extension path **done** (#65) |

---

## Database layer

| API | Status | Evidence |
|-----|--------|----------|
| Connections / `fromConfig` / `DB` / transactions | **done** | `Database.kt`, `DB.kt` |
| Grammar + SchemaGrammar registries | **done** | M25 + #65 |
| Multi-DB docs | **done** | `cadence/database.mdx` (M25 rewrite) |
| Per-engine schema specialization | **done** | M25 |

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
| when / unless / tap / locks / cursor / lazy / chunkById | **done** | #65 + M25 dialect locks |
| `whereDate` / `Day` / `Month` / `Year` / `Time` (+ `or…`) | **done** | B2 + M25 per-engine date SQL |
| `havingRaw` / `orHaving` / `orHavingRaw` | **done** | B2 |
| Global scopes | **done** | B3 — `Scope` + `addGlobalScope` / `withoutGlobalScope(s)` |
| `union` / `unionAll` / `crossJoin` | **done** | B6 |
| `pluck(column, key)` keyed | **done** | B6 QueryBuilder + ModelBuilder + CadenceCollection |
| `sole` | **done** | B6 (+ `MultipleRecordsFoundException`) |
| Native SQL upsert | **done** | M25: `ON CONFLICT` / `ON DUPLICATE KEY` / `MERGE` + portable fallback |

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
| **M25** | First-class engines (SQLite, MariaDB, MySQL, PostgreSQL, Oracle, SQL Server + H2 default) | **done** (this PR) |

---

## Docs

- Public: `apps/docs/.../cadence/database.mdx`, query-builder, models, relations
- Internal: this ledger
