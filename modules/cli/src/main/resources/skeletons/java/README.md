# {{appName}}

Kona application (Java).

## Everyday commands

From this directory (the project root):

```bash
./grind serve
./grind make:controller Post
./gradlew run
```

`HOST` / `PORT` come from `.env` (default `127.0.0.1:8080`).

Create more apps with the **global** `kona` CLI:

```bash
kona new other-app --lang=java
```

## Layout

- `bootstrap/App.java` — boots Kona, registers providers and routes, starts HTTP
- `app/Http/Controllers` — controllers (`./grind make:controller Name`)
- `app/Providers` — service providers
- `routes/WebRoutes.java` — route definitions
- `./grind` — app-local CLI

Kona artifacts resolve from `mavenLocal()` / Maven Central. Against a local checkout:
`-Pkona.home=/path/to/kona` or `KONA_HOME`.
