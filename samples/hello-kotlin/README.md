# hello-kotlin

Kona application (Kotlin) — default **web** kit under `src/main`.

This sample is a **monorepo Gradle module** inside the Kona checkout. It mirrors the `kona new` source layout for regression, but it does **not** ship `./grind` or its own `./gradlew`. Those scripts are created by `kona new` for standalone apps.

## Layout

- `src/main/kotlin/bootstrap` — `App` + `Middleware` (session + CSRF)
- `src/main/kotlin/app/http/controllers/WelcomeController` — `/` renders Crema welcome
- `src/main/resources/views/welcome.crema.html` — welcome view
- `src/main/kotlin/routes` — web + api
- `src/main/resources/config` — JSON config (`app`, `http`, `paths`, `session`)

## Commands

From the Kona **repository root** (not from this directory alone):

```bash
./gradlew :hello-kotlin:run
./gradlew :hello-kotlin:test
```

## Want `./grind`?

Install the global CLI and scaffold a standalone app:

```bash
# from the Kona checkout
./scripts/install-kona.sh
kona new my-app
cd my-app
./grind serve
```

See the public [Installation](https://konaframework.dev/docs/guides/installation/) guide.
