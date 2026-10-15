# {{appName}}

Kona application (Kotlin) — **web** starter kit (Crema views).

## Everyday commands

```bash
./grind serve
./grind make:view home
./gradlew run
```

## What this kit includes

- `dev.kona:kona-crema` dependency
- `CremaServiceProvider` registered in `bootstrap/App.kt`
- `WelcomeController` on `/` rendering `src/main/resources/views/welcome.crema.html`
- JSON health check at `/api/health`

## Resolving Kona artifacts

Publish from a Kona checkout with `./gradlew publishToMavenLocal`, or point
`KONA_HOME` / `-Pkona.home` at a checkout (see `settings.gradle.kts`).
