# {{appName}}

Kona application (Kotlin) — complete Almasix-shaped tree under `src/main`.

## Layout

- `src/main/kotlin/bootstrap` — `App` + `Middleware`
- `src/main/kotlin/app` — providers, HTTP, models (`User`)
- `src/main/kotlin/routes` — web + api
- `src/main/kotlin/database` — migrations + seeders
- `src/main/resources/public/branding` — Kona logo
- `storage/` — writable runtime data
- `.env` / `.env.example` — environment

Path helpers: `app.appPath()`, `app.storagePath()`, `Paths.config()`, …

## Commands

```bash
./grind migrate
./grind db:seed
./grind serve
./grind make:controller Post
./gradlew run
```

Demo login (web kits): `demo@kona.test` / `password` after migrate + seed.
