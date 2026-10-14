# {{appName}}

Kona application (Java) — **Inertia** starter kit.

## Everyday commands

```bash
./grind serve
./grind assets:vite
npm install && npm run dev
npm run build
```

## What this kit includes

- `kona-inertia` + `kona-crema`
- Root Crema shell at `resources/views/app.crema.html`
- `HandleInertia` middleware sharing `appName`
- Example `Inertia.render("Pages/Welcome", …)` route
- Vite stubs (`package.json`, `vite.config.js`, `resources/js/app.js`)

## Resolving Kona artifacts

Publish from a Kona checkout with `./gradlew publishToMavenLocal`, or point
`KONA_HOME` / `-Pkona.home` at a checkout (see `settings.gradle.kts`).
