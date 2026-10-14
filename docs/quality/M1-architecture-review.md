# M1 architecture quality review

Source: [M1 architecture quality review](6b11b4a1-f9c2-4ab3-9661-b6665bfb8ba6) (Claude Opus).  
Status: **P0s closed** on `milestone/M1-completion`. Formal parity gate: [M1-parity-audit.md](M1-parity-audit.md).

## Headline

M1 public surfaces now match the Laravel/Almasix slice for Application/Container/Providers/Config/HTTP Kernel/Router/Middleware/Validation and grind `new`/`serve`/`make:controller`.

## Applied (exhaustion pass)

- Application builder + deferred boot order
- Container thread-safety, `@Inject`/greedy autowiring, circular dependency detection
- Nested config + file/directory load
- `Handler` SAM; Java fluent groups (`prefix`/`name`/`middleware`/`group`)
- Global middleware wraps routing + errors; HEAD→GET; 405 + `Allow`; OPTIONS auto
- Validation fail-closed unknown rules; type-aware min/max
- `grind new` buildable Gradle app; `grind serve` runs user app
- Kotlin + Java samples: providers, groups, middleware through in-process kernel
- Env/debug/host defaults from earlier review pass

## Non-goals for M1 (deferred)

Netty engine, route trie, KSP discovery, sessions/auth/Crema, Redis, GitHub rulesets.
