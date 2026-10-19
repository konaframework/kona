# {{appName}}

Kona application (Java) — complete Almasix-shaped tree under `src/main`.

## Layout

- `src/main/java/bootstrap` — `App` + `Middleware`
- `src/main/java/app` — providers, HTTP, models (`User`)
- `src/main/java/routes` — web + api
- `src/main/java/database` — migrations + seeders
- `src/main/resources/public/branding` — Kona logo
- `storage/` — writable runtime data
- `.env` / `.env.example` — environment

## Commands

```bash
./grind migrate
./grind db:seed
./grind serve
./grind make:controller Post
./gradlew run
```

Demo login (web kits): `demo@kona.test` / `password` after migrate + seed.
