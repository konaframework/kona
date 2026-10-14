# Kona milestones

Single source of truth for milestone status and Definition of Done.  
Working rules: [AGENTS.md](AGENTS.md). Architecture: [docs/architecture/PLAN.md](docs/architecture/PLAN.md).

**Active milestone:** M1 (M0 complete).

---

## Global Definition of Done (every coding milestone)

- [ ] Parity checklist for the milestone is 100% complete (Laravel and/or Almasix slice).
- [ ] 100% line + branch coverage on in-scope production modules (Kover/JaCoCo; CI enforced).
- [ ] Full regression suite green (this milestone + all prior completed milestones).
- [ ] Kotlin sample and Java sample (or regression paths) exercise new public API.
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

**Status:** active  
**Branch:** `milestone/M1-kernel-http-grind` (create only after M0 on `main`)

**Parity slice:** Laravel Application / Container / Providers / Config / Routing / HTTP Kernel / Middleware / Validation + Artisan `serve` / `make` ≈ Almasix `framework` / `http` / `routing` / console bootstrap.

### Parity checklist

Implementation landed on branch. Quality backlog: [docs/quality/M1-architecture-review.md](docs/quality/M1-architecture-review.md).  
**Formal Laravel/Almasix parity inspection + P0 quality items still pending** before M1 merge.

- [x] Application boot (`Kona.start`)
- [x] Service container (bind / singleton / resolve)
- [x] Service providers (register / boot)
- [x] Config + env
- [x] HTTP request / response
- [x] Middleware pipeline
- [x] Router (groups, prefixes, named routes, resource-style registration)
- [x] Kotlin route DSL **and** Java fluent registration
- [x] Exception handling
- [x] Logging
- [x] Validation (request rules)
- [x] `grind serve`
- [x] `grind new` (`--lang=kotlin|java`)
- [x] `grind make:controller`
- [x] Kotlin hello sample
- [x] Java hello sample (no Kotlin sources in the Java app)
- [ ] Parity inspection vs Laravel/Almasix (behavioral review — gate for merge)

### In-scope modules

`kona-core`, `kona-http`, `kona-grind` (as introduced)

---

## M2 — Extract

**Status:** blocked  
**Branch:** `milestone/M2-extract`

**Parity slice:** Eloquent / Almasix ORM.

### Parity checklist

- [ ] Connection manager
- [ ] Query builder
- [ ] Models (ActiveRecord)
- [ ] Relations: hasOne, hasMany, belongsTo, belongsToMany
- [ ] Eager loading
- [ ] Pagination
- [ ] Soft deletes
- [ ] Casts
- [ ] Migrations + schema DSL (Kotlin) / builder (Java)
- [ ] Seeders
- [ ] Factories
- [ ] `grind make:model` / `make:migration` / `migrate`

### In-scope modules

`kona-extract`

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
