# Crema for VS Code and Cursor

Syntax highlighting for Crema templates and an optional launch hook for **kona-lsp**.

## Install (development)

From this directory (or the repo root):

```bash
# VS Code
code --install-extension "$(pwd)"

# Cursor
cursor --install-extension "$(pwd)"
```

Or copy/symlink `ide-support/vscode-crema` into your extensions folder and reload the window.

## Language features (no LSP required)

- File associations: `*.crema.html`, `*.crema.kt`
- TextMate grammar (directives, Brew `wire:*` attributes, `{{ }}`, `{!! !!}`, `{{-- --}}`)
- Language configuration (comments, folding)

## Language server (kona-lsp)

1. Build the jar from the Kona repo:

   ```bash
   ./gradlew :kona-lsp:jar
   ```

   The jar is under `modules/lsp/build/libs/` (Main-Class `org.konaframework.lsp.LspKt`).  
   Or use the application distribution: `./gradlew :kona-lsp:installDist` and run `modules/lsp/build/install/kona-lsp/bin/kona-lsp`.

2. In VS Code / Cursor settings:

   ```json
   {
     "crema.lsp.enabled": true,
     "crema.lsp.javaPath": "java",
     "crema.lsp.jarPath": "/absolute/path/to/kona-lsp-0.1.0-SNAPSHOT.jar"
   }
   ```

3. Use **Crema: Show kona-lsp setup help** from the command palette, or wire an LSP client (for example `vscode-languageclient`) to:

```text
java -jar /path/to/kona-lsp.jar
```

See also: `./grind ide:lsp` for a printed reminder, and the public docs under **Language / Editor**.

## Sync grammar from `ide-support/crema`

```bash
node scripts/sync-grammar.js
```
