# {{appName}}

Kona application (Kotlin).

## Everyday commands

From this directory (the project root):

```bash
./grind serve                 # builds and runs via ./gradlew run
./grind make:controller Post
./grind make:controller Post --resource   # index/create/store/show/edit/update/destroy
./gradlew run                 # same as serve, without grind
```

`HOST` / `PORT` come from `.env` (default `127.0.0.1:8080`).

Create more apps with the **global** `kona` CLI (not grind):

```bash
kona new other-app
kona new ui-app --kit=inertia   # api | crema | inertia | brew
```

## Resolving Kona artifacts

`build.gradle.kts` depends on `dev.kona:kona-http` / `kona-grind` at `{{konaVersion}}` from **mavenLocal()** then Maven Central.

- **Published SNAPSHOT / release:** install once from a Kona checkout:
  `./gradlew publishToMavenLocal` (or consume a release from Central when available).
- **Composite build against a checkout:** `export KONA_HOME=/path/to/kona` or
  `./gradlew run -Pkona.home=/path/to/kona` (see `settings.gradle.kts`).

Without one of those, Gradle cannot resolve `0.1.0-SNAPSHOT`.

## Wiring more of the framework

`grind make:model` / `make:migration` / `make:view` / `migrate` / `queue:*` / `schedule:*`
generate code or boot commands that need the matching module **and** a service provider
registered in `bootstrap/App.kt` (see comments in `app/Providers/AppServiceProvider.kt`).
The default skeleton only registers `HttpServiceProvider` so a minimal HTTP app boots cleanly.

## Layout

- `bootstrap/App.kt` — boots Kona, registers providers and routes, starts HTTP
- `app/Http/Controllers` — controllers (`./grind make:controller Name`)
- `app/Providers` — service providers
- `routes/web.kt` — route definitions
- `./grind` — app-local CLI
