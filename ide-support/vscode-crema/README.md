# Crema for VS Code and Cursor

Syntax highlighting for Crema templates — including **inline Wire `template()` strings** in
Java and Kotlin — Firefly type and method completion from the generated catalog — and an optional launch hook for **kona-lsp**.

## Install (development)

From this directory (or the repo root):

```bash
# VS Code
code --install-extension "$(pwd)"

# Cursor
cursor --install-extension "$(pwd)"
```

Reload the window (`Developer: Reload Window`) after install or after pulling grammar
changes. If highlighting does not appear, uninstall any older `crema` extension first,
then install from this folder again.

## Language features (no LSP required)

- File associations: `*.crema.html`, `*.crema.kt`
- TextMate grammar (directives, Wire `wire:*` attributes, `{{ }}`, `{!! !!}`, `{{-- --}}`)
- Language configuration (comments, folding)
- **Injections into Java/Kotlin** text blocks and string literals that look like templates:
  a `template()` return or assignment, an `inline(` argument, or a `// language=Crema`
  / `/* language=Crema */` comment on the previous line. Crema tokens (`{{`, `@if`,
  `wire:click`, …) are also colored inside Java/Kotlin text blocks.

### Inline Wire `template()` strings

Prefer a `views/wire/<name>.crema.html` file when the markup is large. For single-file
components, Crema highlighting applies to the `template()` text block automatically:

```java
@Override
public String template() {
    return """
        <div>
            <p>Count: {{ count }}</p>
            <button type="button" wire:click="increment">+</button>
        </div>
        """;
}
```

`Component.template()` is annotated `@CremaTemplate`. `Component.inline(source)` and
`WireView.inline(source)` mark the `source` parameter the same way so IntelliJ (Kona Idea)
can inject Crema. You can also write `// language=Crema` on the line above any string.

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
     "crema.lsp.jarPath": "/absolute/path/to/kona-lsp-0.2.0.jar"
   }
   ```

3. Use **Crema: Show kona-lsp setup help** from the command palette, or wire an LSP client
   (for example `vscode-languageclient`) with document selector languages **`crema`**,
   **`java`**, and **`kotlin`**:

```text
java -jar /path/to/kona-lsp.jar
```

Inside Java/Kotlin, kona-lsp only completes and diagnoses **inline Crema ranges**
(`template()` strings, `inline(` arguments, `language=Crema` comments). Completions
cover `@` directives, Wire `wire:*` attributes, and public members of the enclosing
class when the caret is inside `{{ }}`.

See also: `./grind ide:lsp` for a printed reminder, and the public docs under **Language / Editor**.

## Sync grammar from `ide-support/crema`

```bash
node scripts/sync-grammar.js
```
