# Cadence ↔ Eloquent / Database parity ledger

Living audit of `org.konaframework.cadence` against Laravel Eloquent / query builder / database layer (and Almasix ORM where named). Prefer Almasix shape when both exist.

**Status:** `done` | `partial` | `missing`

| Meta | Value |
|------|--------|
| Last audit | 2026-10-19 (M25 complete) |
| Last merged deepen | PR #87 (B6 Eloquent parity) |
| Active deepen | **none** — M25 first-class database engines merged (#161) |

---

## SQLite as scaffold default (decision)

**Why SQLite is the `kona new` default**

1. **Zero install** — no local Postgres/MySQL/H2 daemon; one file on disk.
2. **Survives `./grind` processes** — `jdbc:sqlite:database/database.sqlite` is shared by migrate, seed, and serve (unlike `jdbc:h2:mem:…`, which dies with each JVM).
3. **Familiar layout** — project-root `database/database.sqlite` (gitignored); package code stays under `src/.../database/` for migrations/seeders.
4. **H2 remains first-class** — `kona new --database=h2` (file + `AUTO_SERVER`) and mem URLs in the Cadence **test** suite.

**Decision:** **SQLite file is the scaffold/default** for local demos. H2 stays for tests and optional `--database=h2`. Document PostgreSQL / MySQL / MariaDB / SQL Server / Oracle as production (or heavier) targets.

**Classpath:** skeletons include `runtimeOnly("org.xerial:sqlite-jdbc:…")`. `kona new --database=<engine>` sets `DB_CONNECTION` / `DB_URL` and adds that engine’s JDBC driver when needed.

Evidence: `DatabaseEngines.DEFAULT_NAME`, skeletons, `apps/docs/.../cadence/database.mdx`.

---

## Supported databases / grammars (M25)

| Engine | Query `Grammar` | Schema `SchemaGrammar` | Notes |
|--------|-----------------|------------------------|-------|
| **SQLite** | `Grammar.SQLITE` | `SqliteSchemaGrammar` | Scaffold default (`database/database.sqlite`); alter limits documented |
| **H2** | `Grammar.H2` | `H2SchemaGrammar` | Tests + `--database=h2`; upsert `ON CONFLICT` |
| **PostgreSQL** | `Grammar.POSTGRES` | `PostgresSchemaGrammar` | Identity, JSONB |
| **MySQL** | `Grammar.MYSQL` | `MySqlSchemaGrammar` | `AUTO_INCREMENT`, backticks |
| **MariaDB** | `Grammar.MARIADB` | `MariaDbSchemaGrammar` | Distinct product; shared MySQL DDL family |
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
| **M25** | First-class engines (SQLite, MariaDB, MySQL, PostgreSQL, Oracle, SQL Server + H2 default) | **done** (#161) |

---

## Docs

- Public: `apps/docs/.../cadence/database.mdx`, query-builder, models, relations
- Internal: this ledger
