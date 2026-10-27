# {{appName}}

Kona application (Java) — complete Almasix-shaped tree under `src/main`.

## Layout

- `src/main/java/bootstrap` — `App` + `Middleware`
- `src/main/java/app` — providers, HTTP, models (`User`)
- `src/main/java/routes` — web + api
- `src/main/java/database` — migrations + seeders
- `public/branding` — Kona logo (HTTP document root; Vite writes `public/build`)
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

Demo login (web / Firefly kits): `demo@kona.test` / `password` after migrate + seed. Firefly also accepts those credentials at `/admin/login`.

## Production

Do not use `./grind serve` as the production process. Build with `./gradlew installDist` (or `distZip`) and run `build/install/<app>/bin/<app>` with `APP_ENV=production` and `APP_DEBUG=false`. See the public docs guide **Deployment**.
