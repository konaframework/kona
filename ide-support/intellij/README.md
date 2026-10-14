# Crema in IntelliJ IDEA

**Preferred:** install the native **Kona Idea** plugin from the separate repository
[`konaframework/kona-idea`](https://github.com/konaframework/kona-idea).

That plugin registers Crema as a first-class language (not TextMate), layers HTML
highlighting under `@directives` / `{{ }}` / `{!! !!}` / `{{-- --}}`, offers
directive completion, and ships a Grind run configuration.

| | |
| --- | --- |
| Plugin id | `dev.kona.idea` |
| Releases | [kona-idea Releases](https://github.com/konaframework/kona-idea/releases) (`.zip`) |
| Develop | clone `kona-idea` → `./gradlew runIde` / `buildPlugin` |

Install: **Settings → Plugins → ⚙ → Install Plugin from Disk…** → pick the zip → restart.

Confirm `*.crema.html` / `*.crema.kt` open as language **Crema**.

## Optional: TextMate (legacy)

If you cannot install the native plugin, IntelliJ can still use the TextMate
grammar under [`../crema/`](../crema/):

1. **Settings → Editor → TextMate Bundles** → add `ide-support/crema`.
2. Optionally associate file patterns under **Editor → File Types**.

## Optional: kona-lsp

The native plugin does **not** start `kona-lsp`. For an LSP-compatible client:

```bash
./gradlew :kona-lsp:jar
java -jar modules/lsp/build/libs/kona-lsp-*.jar
```

Or `./grind ide:lsp` from an app. The plugin menu **Kona → Show kona-lsp Setup Help** prints the same reminder.

## Related

- Native plugin: https://github.com/konaframework/kona-idea
- Grammar sources: `ide-support/crema/`
- VS Code / Cursor extension: `ide-support/vscode-crema/`
- JVM module: `modules/lsp` (`dev.kona:kona-lsp`)
