# {{appName}}

Kona application (Kotlin).

```bash
grind serve              # builds and runs the app via ./gradlew run
./gradlew run            # same, without grind
grind serve --in-process # serve compiled classes (run ./gradlew classes first)
```

`HOST` / `PORT` are read from `.env` (default `127.0.0.1:8080`).

Layout:

- `bootstrap/App.kt` — boots Kona, registers providers and routes, starts the HTTP server
- `app/Http/Controllers` — controllers (`grind make:controller Name`)
- `app/Providers` — service providers
- `routes/web.kt` — route definitions

Kona artifacts resolve from `mavenLocal()` / Maven Central. To build against a local
Kona checkout, pass `-Pkona.home=/path/to/kona` or set `KONA_HOME`.
