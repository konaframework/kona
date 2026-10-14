# Kona milestones

Single source of truth for milestone status and Definition of Done.  
Working rules: [AGENTS.md](AGENTS.md). Architecture: [docs/architecture/PLAN.md](docs/architecture/PLAN.md).

**Active milestone:** M2 (M0–M1 complete).

Public site docs (`apps/docs/`, Astro + Starlight + `@almasix/starlight-theme`) are written **concurrently** with each coding milestone — see [docs/governance/docs-site.md](docs/governance/docs-site.md).

---

## Global Definition of Done (every coding milestone)

- [ ] Parity checklist for the milestone is 100% complete (Laravel and/or Almasix slice).
- [ ] 100% line + branch coverage on in-scope production modules (Kover/JaCoCo; CI enforced).
- [ ] Full regression suite green (this milestone + all prior completed milestones).
- [ ] Kotlin sample and Java sample (or regression paths) exercise new public API.
- [ ] Public docs for the slice updated under `apps/docs/` (guides + sidebar).
- [ ] Milestone PR merged to `main` via required checks + automerge.
- [ ] `MILESTONES.md` updated: milestone marked **done**, next marked **active**.

---

## M0 — Plan + rules lock

**Status:** done  
**Branch:** `milestone/M0-plan` (merged)  
**Parity scope:** N/A (process)

### Deliverables

- [x] GitHub repo `konaframework/kona`
- [x] `docs/architecture/PLAN.md` (canonical architecture plan)
- [x] `docs/architecture/components.md` (component map)
- [x] `AGENTS.md` (agent laws)
- [x] `.cursor/rules/kona-milestones.mdc` (`alwaysApply`)
- [x] `MILESTONES.md` (this file)
- [x] `README.md` + `LICENSE`
- [x] Merged to `main`

### DoD

- [x] Docs on `main`; agents can enforce rules from a fresh clone.

---

## M1 — Kona kernel + HTTP + grind

**Status:** done  
**Branch:** `milestone/M1-completion` (merged; scaffold was `milestone/M1-kernel-http-grind`)

**Parity slice:** Laravel Application / Container / Providers / Config / Routing / HTTP Kernel / Middleware / Validation + Artisan `serve` / `make` ≈ Almasix `framework` / `http` / `routing` / console bootstrap.

### Parity checklist

Quality + parity audits: [docs/quality/M1-architecture-review.md](docs/quality/M1-architecture-review.md), [docs/quality/M1-parity-audit.md](docs/quality/M1-parity-audit.md).  
**Verdict: complete** on `main`.

- [x] Application boot (`Kona.start` + `Kona.create().providers().boot()` builder)
- [x] Service container (bind / singleton / resolve / autowire / thread-safe / cycles)
- [x] Service providers (register / boot — all register before any boot)
- [x] Config + env (nested dotted keys; file/directory load; env precedence)
- [x] HTTP request / response (typed data, headers, HEAD body strip)
- [x] Middleware pipeline (global wraps routing + errors; configurable)
- [x] Router (groups, prefixes, named routes, resource, HEAD→GET, 405/Allow, OPTIONS, `any`)
- [x] Kotlin route DSL **and** Java fluent registration (prefix/name/middleware/group)
- [x] Exception handling (JSON envelope; Allow on 405; validation 422)
- [x] Logging — basic facade
- [x] Validation (Laravel-shaped rules; fail unknown; type-aware min/max/size)
- [x] `grind serve` — runs generated app (`gradlew run` or `--in-process`)
- [x] `kona new` (`--lang=kotlin|java`) — global CLI; buildable Gradle app + local `./grind`
- [x] `grind make:controller` — basic + name validation
- [x] Kotlin hello sample — providers, groups, middleware via HttpKernel
- [x] Java hello sample — groups, middleware, providers (Law 8)
- [x] Parity inspection vs Laravel/Almasix (audits updated; blockers closed)

### In-scope modules

`kona-core`, `kona-http`, `kona-grind` (as introduced)

---

## M2 — Extract

**Status:** active  
**Branch:** `milestone/M2-extract`

**Parity slice:** Eloquent / Almasix ORM.

### Parity checklist

- [x] Connection manager
- [x] Query builder
- [x] Models (ActiveRecord)
- [x] Relations: hasOne, hasMany, belongsTo, belongsToMany
- [x] Eager loading
- [x] Pagination
- [x] Soft deletes
- [x] Casts
- [x] Migrations + schema DSL (Kotlin) / builder (Java)
- [x] Seeders
- [x] Factories
- [x] `grind make:model` / `make:migration` / `migrate`

### In-scope modules

`kona-extract`, `kona-grind` (database commands)

---

## M3 — Auth, session, cache

**Status:** blocked  
**Branch:** `milestone/M3-auth-session-cache`

### Parity checklist

- [ ] Session drivers (cookie, file, Redis as applicable)
- [ ] Auth guards / providers
- [ ] Password hashing
- [ ] Policies / gates (basic)
- [ ] Cache stores (memory, Redis)

---

## M4 — Queue, events, mail, filesystem

**Status:** blocked  
**Branch:** `milestone/M4-queue-events-mail-fs`

### Parity checklist

- [ ] Job dispatch, workers, retries, failed jobs
- [ ] Event bus + listeners (SAM-friendly for Java)
- [ ] Mail + notifications (log + SMTP minimum)
- [ ] Filesystem disks (local + S3-compatible)

---

## M5 — Crema, steam, drip (+ harness polish)

**Status:** blocked  
**Branch:** `milestone/M5-crema-steam-drip`

### Parity checklist

- [ ] Crema: `*.crema.html` / `*.crema.kt`
- [ ] Directives: `@extends`, `@section`, `@yield`, `@if`, `@foreach`, `@component`
- [ ] `grind steam` REPL (Kotlin + Java expressions in app context)
- [ ] `drip` fluent HTTP client module
- [ ] Testing harness (Artisan/`smith` test equivalent)
- [ ] Scheduling (as in Laravel/Almasix for this wave)

---

## M6+ — Later (separate exhausted milestones)

Not started until M5 is on `main`. Each item below becomes its own milestone with the same global DoD:

- Broadcasting
- Scout / search
- IDE / LSP (`ide-support` repo)
- Orbit-class admin (if pursued)
