# hello-java

Kona application (Java) — default **web** kit under `src/main`.

## Layout

- `src/main/java/bootstrap` — `App` + `Middleware` (session + CSRF)
- `src/main/java/app/http/controllers/WelcomeController` — `/` renders Crema welcome
- `src/main/resources/views/welcome.crema.html` — welcome view
- `src/main/java/routes` — web + api
- `src/main/resources/config` — JSON config (`app`, `http`, `paths`, `session`)

## Commands

From the Kona repo root:

```bash
./gradlew :hello-java:run
./gradlew :hello-java:test
```
