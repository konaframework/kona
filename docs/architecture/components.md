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

- Brand colors primary `#FF9A00`, accent `#286291`
- Methods: `info` / `success` / `warn` / `error`, `title`, `box`, `twoColumn`, `progress`
- Env: `NO_COLOR` (off), `FORCE_COLOR=1` (on), `FORCE_COLOR=0` (off), `TERM=dumb` (off)

| Surface | Type |
| --- | --- |
| Shared API | `kona-support` → `ConsoleIO` |
| `./grind` | `GrindIO` (wraps `ConsoleIO` + quiet/verbose) |
| global `kona` | `ConsoleIO` directly |
| `scripts/install.sh` | Same visual language (bash ANSI) |

**Do not** use raw `println` / unstyled `System.out` for user-facing CLI output. New commands must go through `ConsoleIO` or `GrindIO`.

### `kona` (global CLI — `kona-cli`)

Installed on the developer machine PATH. Primary command: `kona new --lang=kotlin|java`. Output via `ConsoleIO`.

### `grind` (app-local CLI — `kona-grind`)

Shipped inside each generated application as `./grind`. Target commands: `./grind serve`, `./grind make:model`, `./grind make:controller`, `./grind make:migration`, `./grind migrate`, `./grind steam`. **Not** used to create new apps. Output via `GrindIO` → `ConsoleIO`.

### `steam` (REPL)

Integrated into `grind`. Evaluates Kotlin/Java expressions inside the bootstrapped application context.

### `Cadence` (ORM)

ActiveRecord with companion / static query helpers. Fluent builder: `Cadence.where()`, `Cadence.find()`, `hasMany()`, `belongsTo()`, and related Eloquent-class APIs.

### `Crema` (views)

Directive-driven templating compiler. Core directives: `@extends`, `@section`, `@yield`, `@if`, `@foreach`, `@component`.

### `drip` (client / SDK)

Fluent HTTP client for external APIs and frontend → Kona backend calls.

### Brew (reactive UI)

Full-stack reactive server components. Lives in the separate repo
[konaframework/kona-brew](https://github.com/konaframework/kona-brew) (`org.konaframework:kona-brew`, package `org.konaframework.brew`).
Behavioral/API-shape parity with Livewire v4 is locked in that repo’s
[livewire-v4-parity.md](https://github.com/konaframework/kona-brew/blob/main/docs/governance/livewire-v4-parity.md).


**Inertia** lives in [konaframework/kona-inertia](https://github.com/konaframework/kona-inertia) (`org.konaframework:kona-inertia`, package `org.konaframework.inertia`). Docs: https://inertia.konaframework.org/.
Public guides: `apps/docs` → **Brew**. Starter kit: `kona new --kit=brew`.
