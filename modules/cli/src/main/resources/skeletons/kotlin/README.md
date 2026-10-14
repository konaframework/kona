# {{appName}}

Kona application (Kotlin).

## Everyday commands

From this directory (the project root):

```bash
./grind serve              # builds and runs via ./gradlew run
./grind make:controller Post
./gradlew run              # same as serve, without grind
```

`HOST` / `PORT` come from `.env` (default `127.0.0.1:8080`).

Create more apps with the **global** `kona` CLI (not grind):

```bash
kona new other-app
```

## Layout

- `bootstrap/App.kt` — boots Kona, registers providers and routes, starts HTTP
- `app/Http/Controllers` — controllers (`./grind make:controller Name`)
- `app/Providers` — service providers
- `routes/web.kt` — route definitions
- `./grind` — app-local CLI

Kona artifacts resolve from `mavenLocal()` / Maven Central. Against a local checkout:
`-Pkona.home=/path/to/kona` or `KONA_HOME`.
