# Brew LSP / IDE contract notes (DX-6)

Kona Idea and language servers can offer completions for Brew markup and `wire:*` attributes
using this contract. No runtime dependency is required—this documents the surface for IDE plugins.

## Tags

| Tag / directive | Completion |
| --- | --- |
| `<brew:name … />` | Component names from `Brew.discover` / registered components |
| `@brew('name', …)` | Same name catalog |
| `@brewStyles` / `@brewScripts` / `@brewScriptConfig` | Exact tokens |
| `@js(…)` / `@script` | Script islands |

## `wire:*` attributes

| Prefix | Notes |
| --- | --- |
| `wire:model[.live\|.blur\|.change\|.lazy\|.debounce\|.throttle\|.number\|.boolean\|.fill]` | Property paths |
| `wire:click` / `wire:submit` / `wire:*` | Action methods + modifiers (`.prevent`, `.stop`, `.window`, …) |
| `wire:loading` / `wire:dirty` / `wire:offline` / `wire:cloak` | State directives |
| `wire:navigate` / `wire:current` / `wire:poll` / `wire:intersect` / `wire:sort` | Navigation & platform |
| `wire:stream` / `wire:ignore` / `wire:key` / `wire:ref` | Morph / refs |

Root attributes emitted by Brew: `wire:id`, `wire:name`, `wire:snapshot`, `wire:effects`.

## Annotations (Kotlin / Java)

`@Locked`, `@Computed`, `@Validate` / `@Rule`, `@Layout`, `@Title`, `@Url`, `@Session`,
`@Lazy` / `@Defer`, `@Isolate`, `@Reactive`, `@Modelable`, `@Authorize`, `@Renderless`, `@Async`, `@Js`, `@On`.

IDE plugins should resolve these from `org.konaframework.brew` and offer import assists.
