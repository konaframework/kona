# Kona component map

Kona is a full-stack, developer-friendly web framework for the Java/Kotlin ecosystem, inspired by Laravel’s design philosophy and Almasix’s shape, using coffee-themed nomenclature.

## Core mapping

| Kona component | Laravel equivalent | Almasix equivalent | Role |
| :--- | :--- | :--- | :--- |
| **Kona** (framework) | Laravel | Almasix core | Framework core, service container, auto-wiring, routing, bootloader |
| **`kona` CLI** | `laravel` installer | — | Global CLI to create applications (`kona new`) |
| **grind** | Artisan | `smith` | **App-local** CLI (`./grind`) for serve, generators, migrate, steam |
| **steam** | Tinker | (REPL) | Interactive REPL in bootstrapped app context |
| **Extract** | Eloquent | Almasix ORM | ActiveRecord ORM, query builder, relationships, migrations |
| **Crema** | Blade / Prism | Prism | Server-side templates with layouts, directives, components |
| **drip** | Axios / HTTP client | HTTP client | Fluent HTTP client/SDK |

## Ecosystem conventions

```text
Global installer:  kona new my-app
App CLI:           ./grind | ./grind steam
View Files:        *.crema.html (or *.crema.kt)
Model Base:        Extract
Main Boot:         Kona.create(args).boot()
```

## Component specs (agent guidance)

### `kona` (global CLI — `kona-cli`)

Installed on the developer machine PATH. Primary command: `kona new --lang=kotlin|java`.

### `grind` (app-local CLI — `kona-grind`)

Shipped inside each generated application as `./grind`. Target commands: `./grind serve`, `./grind make:model`, `./grind make:controller`, `./grind make:migration`, `./grind migrate`, `./grind steam`. **Not** used to create new apps.

### `steam` (REPL)

Integrated into `grind`. Evaluates Kotlin/Java expressions inside the bootstrapped application context.

### `Extract` (ORM)

ActiveRecord with companion / static query helpers. Fluent builder: `Extract.where()`, `Extract.find()`, `hasMany()`, `belongsTo()`, and related Eloquent-class APIs.

### `Crema` (views)

Directive-driven templating compiler. Core directives: `@extends`, `@section`, `@yield`, `@if`, `@foreach`, `@component`.

### `drip` (client / SDK)

Fluent HTTP client for external APIs and frontend → Kona backend calls.

### Reactive UI (Livewire / Conduit parity)

Full-stack reactive components (M11). **Product name TBD** (coffee-themed); behavioral parity with Livewire / Conduit is locked in [MILESTONES.md](../../MILESTONES.md).
