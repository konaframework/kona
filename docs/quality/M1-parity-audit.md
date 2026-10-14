# M1 Laravel / Almasix parity audit

Source: [M1 Laravel parity audit](fec710fb-9c3e-4cb6-8856-ddc6973e38a9) (Claude Opus).  
Cross-check: [M1 architecture quality review](M1-architecture-review.md).

## Verdict

**Unblocked — merge-ready for M1 exhaustion.** Blockers C1–C4, H1–H3, H9, G1–G2, and S1 are closed on `milestone/M1-completion`. Full suite + Kover 100% line/branch on `kona-core`, `kona-http`, `kona-grind`.

## Closed blockers

| ID | Resolution |
|----|------------|
| C1 | Container: constructor autowiring, `make` ≡ `resolve`, circular-deps, thread-safe |
| C3 | `Application.Builder` / `Kona.create().providers().boot()` — all register before any boot |
| C4 | Nested dotted config; `load` / `loadDirectory` for `.properties` + JSON |
| H1–H3 | Global middleware wraps routing + exception handling; configurable middleware list |
| H9 | Validation rule set expanded; unknown rules fail; type-aware min/max/size/between |
| G1–G2 | `grind new` emits buildable Gradle app; `grind serve` runs `./gradlew run` or `--in-process` |
| S1 | Java sample + `JavaApiTest` exercise groups, middleware, providers |

## Earlier fixes (still hold)

- `APP_DEBUG` defaults false; process env wins over `.env`; quote/`export`/comment parsing
- Default bind `127.0.0.1`; HOST/PORT after `.env`
- Nested provider boot; controller name safety; `.txt` strip on skeletons

## Keep

Module split, BOM, HttpKernel/server separation, middleware pipeline, Kover 100% gate, VT executor, Response `@JvmStatic` factories, `Handler` SAM.
