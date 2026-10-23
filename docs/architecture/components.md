# Kona component map

Kona is a full-stack, developer-friendly web framework for the Java/Kotlin ecosystem, inspired by Laravel’s design philosophy and Almasix’s shape, using coffee-themed nomenclature.

## Core mapping

| Kona component | Laravel equivalent | Almasix equivalent | Role |
| :--- | :--- | :--- | :--- |
| **Kona** (framework) | Laravel | Almasix core | Framework core, service container, auto-wiring, routing, bootloader |
| **`kona` CLI** | `laravel` installer | — | Global CLI to create applications (`kona new`) |
| **grind** | Artisan | `smith` | **App-local** CLI (`./grind`) for serve, generators, migrate, steam |
| **steam** | Tinker | (REPL) | Interactive REPL in bootstrapped app context |
| **Cadence** | Eloquent | Almasix ORM | ActiveRecord ORM, query builder, relationships, migrations |
| **Crema** | Blade / Prism | Prism | Server-side templates with layouts, directives, components |
| **drip** | Axios / HTTP client | HTTP client | Fluent HTTP client/SDK |
| **Wire** | Livewire | — | Reactive server components (HTML over JSON; `wire:*` attributes) |

## Ecosystem conventions

```text
Global installer:  kona new my-app
App CLI:           ./grind | ./grind steam
View Files:        *.crema.html (or *.crema.kt)
Model Base:        Cadence
Main Boot:         Kona.create(args).boot()
```

## Component specs (agent guidance)

### Console / CLI output (locked)

**All** terminal commands use Termwind-inspired styling via [`org.konaframework.support.console.ConsoleIO`](../../modules/support/src/main/kotlin/org/konaframework/support/console/ConsoleIO.kt):

- Brand colors primary caramel `#DF8723` (dark: latte `#C9A57B`), accent warm brown `#74453F` (dark: soft cream `#E8D4B8`)
- Methods: `info` / `success` / `warn` / `error`, `title`, `box`, `twoColumn`, `progress`
- Env: `NO_COLOR` (off), `FORCE_COLOR=1` (on), `FORCE_COLOR=0` (off), `TERM=dumb` (off)

| Surface | Type |
| --- | --- |
| Shared API | `kona-support` → `ConsoleIO` |
| `./grind` | `GrindIO` (wraps `ConsoleIO` + quiet/verbose) |
| global `kona` | `ConsoleIO` directly |
| `scripts/install.sh` | Same visual language (bash ANSI) |

Interactive `ask` / `confirm` / `choice` live in `ConsolePrompts` (`kona-support`) and are wrapped by grind `Prompts` and `kona new` `InstallerPrompts`. On a TTY they enable POSIX raw mode (`stty -echo -icanon`), decode arrow keys, keep the caret inside the box, and replace the live widget in place on submit. Injected streams and non-TTY stdin stay line-based.

**Do not** use raw `println` / unstyled `System.out` for user-facing CLI output. New commands must go through `ConsoleIO` or `GrindIO`.

### `kona` (global CLI — `kona-cli`)

Installed on the developer machine PATH. Primary command: `kona new --lang=kotlin|java`. Output via `ConsoleIO`.

### `grind` (app-local CLI — `kona-grind`)

Shipped inside each generated application as `./grind`. Target commands: `./grind serve`, `./grind make:model`, `./grind make:controller`, `./grind make:migration`, `./grind migrate`, `./grind steam`. **Not** used to create new apps. Output via `GrindIO` → `ConsoleIO`.

### `steam` (REPL)

Integrated into `grind`. Evaluates Kotlin/Java expressions inside the bootstrapped application context.

### `Cadence` (ORM)

ActiveRecord with companion / static query helpers. Fluent builder: `Cadence.where()`, `Cadence.find()`, `hasMany()`, `belongsTo()`, and related Eloquent-class APIs.

**Relational (shipped):** JDBC + HikariCP + SQL `Grammar` / migrations — SQLite, H2, MySQL, MariaDB, PostgreSQL, Oracle, SQL Server (M25).

**Document (Kona 1.x, [M27](../../MILESTONES.md#m27--cadence-document--mongodb-kona-1x)):** MongoDB is a first-class **Cadence document family**, not an eighth SQL engine. Optional artifact `kona-cadence-mongo`; document models share Cadence DX (casts, events, factories, Auth) over collections/BSON. Agents must not implement M27 until a human activates the 1.x train.

### `Crema` (views)

Directive-driven templating compiler. Core directives: `@extends`, `@section`, `@yield`, `@if`, `@foreach`, `@component`.

### `drip` (client / SDK)

Fluent HTTP client for external APIs and frontend → Kona backend calls.

### Wire (reactive UI)

Full-stack reactive server components in this monorepo: `modules/wire` → Maven `org.konaframework:kona-wire` (package `org.konaframework.wire`). JS runtime: `js/wire/`. Docs: [docs.konaframework.org/docs/wire/](https://docs.konaframework.org/docs/wire/) (`apps/docs`). Livewire v4 parity ledger: [livewire-v4-parity.md](../governance/livewire-v4-parity.md). Starter: `kona new --kit=wire`.

**Kona 0.3.0:** product name **Kona Wire** (short name **Wire**). Keep `wire:*` attributes. Coordinates `kona-wire` / `org.konaframework.wire` / `app.wire` / `<wire:…>` with Wire aliases (`kona-brew`, `<brew:…>`, `--kit=brew`) for one train.

### Inertia

SPA adapter in this monorepo: `modules/inertia` → Maven `org.konaframework:kona-inertia` (package `org.konaframework.inertia`). Docs: [docs.konaframework.org/docs/inertia/](https://docs.konaframework.org/docs/inertia/) (`apps/docs`). Starter: `kona new --kit=inertia`.

Sister GitHub repos `konaframework/kona-wire` and `konaframework/kona-inertia` are archived after remonorepo (source of truth is this repo).
