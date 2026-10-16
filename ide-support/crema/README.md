# Crema language grammar

TextMate grammar and language configuration for **Crema** templates (`*.crema.html`, `*.crema.kt`).

| File | Purpose |
|------|---------|
| `crema.tmLanguage.json` | Syntax highlighting for directives (`@extends`, `@section`, `@if`, `@foreach`, `@json`, `@csrf`, …), echoes `{{ }}` / `{!! !!}`, and comments `{{-- --}}` |
| `language-configuration.json` | Comments, brackets, auto-close, folding markers |

Editors consume these via the [VS Code / Cursor extension](../vscode-crema/) or [IntelliJ TextMate bundle](../intellij/). The JVM language server lives in `modules/lsp` (`io.konaframework:kona-lsp`).
