# M1 Laravel / Almasix parity audit

Source: [M1 Laravel parity audit](fec710fb-9c3e-4cb6-8856-ddc6973e38a9) (Claude Opus).  
Cross-check: [M1 architecture quality review](6b11b4a1-f9c2-4ab3-9661-b6665bfb8ba6).

## Verdict

**Blocked — not merge-ready.** Module layout and Kover gates are strong, but several MILESTONES boxes overstated Laravel/Almasix behavioral parity. The `grind new` → `grind serve` golden path does not yet run a user app.

## Blockers (must fix before M1 merge)

| ID | Gap |
|----|-----|
| C1 | Container: no constructor autowiring; `make` ≠ `resolve` |
| C3 | Providers: `Kona.start` boots before providers can register |
| C4 | Config: no file load; flat map only |
| H1–H3 | Global middleware skipped on 404; exceptions bypass “after” middleware; not configurable |
| H9 | Validation stub (5 rules; unknown rules pass; min/max not type-aware) |
| G1–G2 | `grind new` must emit a buildable app; `grind serve` must run it |
| S1 | Java must exercise groups/middleware/providers (Law 8) |

## Already addressed (post-review commits on this branch)

These were open in the audit snapshot; they are **done** on current `milestone/M1-kernel-http-grind` (do not re-open as blockers):

- **C5 / C6 (partial) / C7:** `APP_DEBUG` defaults to **false**; process env wins over `.env`; quotes / `export` / inline `#` comments
- **G1 (partial):** `grind new` strips `.txt` and sets `APP_NAME` from the folder name; controller name validation
- **H12 (partial) / serve:** default bind **127.0.0.1**; HOST/PORT read after `.env` load
- Nested provider boot during `Application.boot()`

Still open from the audit: C1–C4, H1–H3, H9, G1 (Gradle app), G2 (serve user app), S1, and the API-shape items (builder, `Handler`, method-set routes).

## Keep

Module split, BOM, HttpKernel/server separation, middleware `foldRight` pipeline, Kover 100% gate, VT executor pattern, Response `@JvmStatic` factories.

## Next

Work the ordered fix list in [M1-architecture-review.md](M1-architecture-review.md) together with the priority list in the parity audit (application builder → container → routing/Handler APIs → middleware lifecycle → validation → grind golden path → Java regressions).
