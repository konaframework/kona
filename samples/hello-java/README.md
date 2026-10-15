# hello-java

Kona application (Java) — complete Almasix-shaped tree under `src/main`.

## Layout

- `src/main/java/bootstrap` — `App` + `Middleware`
- `src/main/java/app` — providers, HTTP, models
- `src/main/java/routes` — web + api
- `src/main/resources/config` — JSON config
- `storage/` — writable runtime data
- `.env` / `.env.example` — environment

Path helpers: `app.appPath()`, `app.storagePath()`, `Paths.config()`, …

## Commands

```bash
./grind serve
./grind make:controller Post
./gradlew run
```
