# {{appName}}

Kona application (Java) — **Brew** starter kit.

## Everyday commands

```bash
./grind serve
./gradlew run
```

## What this kit includes

- `kona-brew` + `kona-crema`
- `BrewServiceProvider` and a sample `Counter` component
- Welcome page that mounts the counter and includes `brew.js`

## Resolving Kona artifacts

Publish from a Kona checkout with `./gradlew publishToMavenLocal`, or point
`KONA_HOME` / `-Pkona.home` at a checkout (see `settings.gradle.kts`).
