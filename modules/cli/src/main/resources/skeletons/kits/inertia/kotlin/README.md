# {{appName}}

Kona application (Kotlin) — **Inertia** starter kit.

## Everyday commands

```bash
./grind serve
./grind assets:vite          # print Vite setup reminders
npm install && npm run dev   # frontend HMR (writes public/hot)
npm run build                # writes public/build/manifest.json
```

## What this kit includes

- `kona-inertia` + `kona-crema`
- Root Crema shell at `resources/views/app.crema.html` (`data-page` + Vite tags)
- `HandleInertia` middleware sharing `appName`
- Example `Inertia.render("Pages/Welcome", …)` route
- `package.json` / `vite.config.js` / `resources/js/app.js` stubs

Wire a client library (Vue, React, or Svelte) that reads `#app[data-page]` and
visits with the `X-Inertia` header for JSON page responses.

## Resolving Kona artifacts

Publish from a Kona checkout with `./gradlew publishToMavenLocal`, or point
`KONA_HOME` / `-Pkona.home` at a checkout (see `settings.gradle.kts`).
