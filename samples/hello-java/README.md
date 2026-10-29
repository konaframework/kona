# hello-java

Kona application (Java) — default **web** kit under `src/main`.

This sample is a **monorepo Gradle module** inside the Kona checkout. It mirrors the `kona new` source layout for regression, but it does **not** ship `./grind` or its own `./gradlew`. Those scripts are created by `kona new` for standalone apps.

## Layout

- `src/main/java/bootstrap` — `App` + `Middleware` (session + CSRF)
- `src/main/java/app/http/controllers/WelcomeController` — `/` renders Crema welcome
- `src/main/resources/views/welcome.crema.html` — welcome view
- `src/main/java/routes` — web + api
- `src/main/resources/config` — JSON config (`app`, `http`, `paths`, `session`)

## Commands

From the Kona **repository root** (not from this directory alone):

```bash
./gradlew :hello-java:run
./gradlew :hello-java:test
```

On boot the Desk panel migrates and (when empty) seeds demo Firefly data: **Projects**, **Tasks** (Select.relationship → Project combobox), **Notes**, and **Orders** / lines. Open `http://127.0.0.1:4000/admin/tasks/manage` and use **Create** to exercise the combobox. Set `FIREFLY_SAMPLE_SEED=false` to skip migrate/seed.

## Want `./grind`?

Install the global CLI and scaffold a standalone app:

```bash
# Latest Maven Central kona-cli (not git main).
curl -fsSL https://raw.githubusercontent.com/konaframework/kona/main/scripts/install.sh | bash
kona new my-app --lang=java
cd my-app
./grind serve
```

See the public [Installation](https://docs.konaframework.org/docs/guides/installation/) guide.
