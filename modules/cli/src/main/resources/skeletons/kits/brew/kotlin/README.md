# {{appName}}

Kona application (Kotlin) — **Brew** starter kit.

```bash
./grind serve
./gradlew run
```

Includes `kona-brew` (from [konaframework/kona-brew](https://github.com/konaframework/kona-brew)) + `kona-crema`, sample `Todos` (welcome demo) and `Counter` components under `app.brew`, and Crema views under `src/main/resources/views/`.

The installer publishes `kona-brew` to Maven local when a sister checkout is available (`KONA_BREW_SRC` or `../kona-brew`). Otherwise:

```bash
# from a kona-brew checkout
./gradlew :kona-brew:publishToMavenLocal
```
