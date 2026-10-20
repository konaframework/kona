# {{appName}}

Kona application (Kotlin) — **web** starter kit (Crema views).

## Everyday commands

```bash
./grind migrate
./grind db:seed
./grind serve
./grind make:view home
./gradlew run
```

## What this kit includes

- `org.konaframework:kona-crema` dependency (Maven Central)
- Branded landing at `/` (`welcome.crema.html`)
- Session login at `/login` and dashboard at `/dashboard`
- Cadence `User` model, `2000_01_01_000000_create_users_table` migration, and demo seeder (`demo@kona.test` / `password`)
- JSON users API: `GET /api/users`, `GET /api/users/{id}`
- JSON health check at `/api/health`
