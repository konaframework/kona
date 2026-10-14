# Kona component map

Kona is a full-stack, developer-friendly web framework for the Java/Kotlin ecosystem, inspired by Laravel’s design philosophy and Almasix’s shape, using coffee-themed nomenclature.

## Core mapping

| Kona component | Laravel equivalent | Almasix equivalent | Role |
| :--- | :--- | :--- | :--- |
| **Kona** | Laravel | Almasix core | Framework core, service container, auto-wiring, routing, bootloader |
| **grind** | Artisan | `smith` | CLI for scaffolding, migrations, serve, and command execution |
| **steam** | Tinker | (REPL) | Interactive REPL in bootstrapped app context |
| **Extract** | Eloquent | Almasix ORM | ActiveRecord ORM, query builder, relationships, migrations |
| **Crema** | Blade / Prism | Prism | Server-side templates with layouts, directives, components |
| **drip** | Axios / HTTP client | HTTP client | Fluent HTTP client/SDK |

## Ecosystem conventions

```text
CLI Binary:      ./grind
REPL Command:    ./grind steam
View Files:      *.crema.html (or *.crema.kt)
Model Base:      Extract
Main Boot:       Kona.start(args)
```

## Component specs (agent guidance)

### `grind` (CLI)

Primary entry point for developer commands.

Target commands: `grind serve`, `grind make:model`, `grind make:controller`, `grind make:migration`, `grind migrate`, `grind steam`, `grind new` (`--lang=kotlin|java`).

### `steam` (REPL)

Integrated into `grind`. Evaluates Kotlin/Java expressions inside the bootstrapped application context.

### `Extract` (ORM)

ActiveRecord with companion / static query helpers. Fluent builder: `Extract.where()`, `Extract.find()`, `hasMany()`, `belongsTo()`, and related Eloquent-class APIs.

### `Crema` (views)

Directive-driven templating compiler. Core directives: `@extends`, `@section`, `@yield`, `@if`, `@foreach`, `@component`.

### `drip` (client / SDK)

Fluent HTTP client for external APIs and frontend → Kona backend calls.
