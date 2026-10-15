# hello-kotlin

Kona application (Kotlin) — default **web** kit under `src/main`.

## Layout

- `src/main/kotlin/bootstrap` — `App` + `Middleware` (session + CSRF)
- `src/main/kotlin/app/http/controllers/WelcomeController` — `/` renders Crema welcome
- `src/main/resources/views/welcome.crema.html` — welcome view
- `src/main/kotlin/routes` — web + api
- `src/main/resources/config` — JSON config (`app`, `http`, `paths`, `session`)

## Commands

From the Kona repo root:

```bash
./gradlew :hello-kotlin:run
./gradlew :hello-kotlin:test
```
