# {{appName}}

Kona application (Kotlin) — **web** starter kit (Crema views).

## Everyday commands

```bash
./grind serve
./grind make:view home
./gradlew run
```

## What this kit includes

- `org.konaframework:kona-crema` dependency (Maven Central)
- `CremaServiceProvider` registered in `bootstrap/App.kt`
- `WelcomeController` on `/` rendering `src/main/resources/views/welcome.crema.html`
- JSON health check at `/api/health`
