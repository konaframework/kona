# {{appName}}

Kona application (Java).

## Everyday commands

From this directory (the project root):

```bash
./grind serve
./grind make:controller Post
./grind make:controller Post --resource
./gradlew run
```

`HOST` / `PORT` come from `.env` (default `127.0.0.1:8080`).

Create more apps with the **global** `kona` CLI:

```bash
kona new other-app --lang=java
```

## Resolving Kona artifacts

Depends on `dev.kona:kona-http` / `kona-grind` from **mavenLocal()** then Maven Central.
Publish from a Kona checkout with `./gradlew publishToMavenLocal`, or composite-build with
`KONA_HOME` / `-Pkona.home=/path/to/kona`. Without one of those, SNAPSHOT resolution fails.

Generators such as `make:model` / `migrate` / `queue:*` need the matching module dependency
and a service provider registered in `bootstrap/App.java` (see `AppServiceProvider` comments).

## Layout

- `bootstrap/App.java` — boots Kona, registers providers and routes, starts HTTP
- `app/Http/Controllers` — controllers (`./grind make:controller Name`)
- `app/Providers` — service providers
- `routes/WebRoutes.java` — route definitions
- `./grind` — app-local CLI
