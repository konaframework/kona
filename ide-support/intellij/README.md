# Crema in IntelliJ IDEA

Kona ships a TextMate grammar under [`../crema/`](../crema/). IntelliJ can use it via the bundled **TextMate bundles** support (Ultimate and recent Community builds).

## Option A — TextMate bundle

1. Open **Settings → Editor → TextMate Bundles**.
2. Click **+** and select the `ide-support/crema` directory (or a folder that contains `crema.tmLanguage.json`).
3. Confirm the bundle appears and that `*.crema.html` / `*.crema.kt` map to the Crema grammar.
4. Apply / OK, then reopen a Crema view.

If your IDE build expects a classic bundle layout, create a thin bundle directory:

```text
Crema.tmbundle/
  Syntaxes/crema.tmLanguage.json   # copy from ide-support/crema/
  Preferences/                     # optional
```

## Option B — File type association

1. **Settings → Editor → File Types**.
2. Add a file type named **Crema** (or associate with HTML).
3. Register patterns: `*.crema.html`, `*.crema.kt`.
4. Pair with the TextMate grammar above for highlighting; without TextMate you still get HTML-like editing.

## Language server

IntelliJ LSP support varies by version. Point any LSP-compatible client at:

```bash
java -jar /path/to/kona-lsp.jar
```

Build the jar with `./gradlew :kona-lsp:jar` from the Kona repository.  
`./grind ide:lsp` prints the same reminder without starting a long-lived process.

## Related

- Grammar sources: `ide-support/crema/`
- VS Code / Cursor extension: `ide-support/vscode-crema/`
- JVM module: `modules/lsp` (`dev.kona:kona-lsp`)
