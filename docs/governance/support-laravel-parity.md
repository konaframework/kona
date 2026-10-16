# Support utilities ↔ Laravel / Almasix parity ledger

Living audit of `kona-support` Collections, `Arr`, `Str` / `Stringable`, and `KonaDate` against Laravel Collections / helpers / Carbon-class shapes (Almasix where named).

**Status legend:** `done` | `partial` | `missing` | `deferred`

| Meta | Value |
|------|--------|
| Last audit | 2026-10-16 |
| Baseline | M4 support utilities |
| Active deepen | **closed** — Collection deepen + Arr/Str/Date gaps |

---

## 1. Collections

| API | Status | Evidence |
|-----|--------|----------|
| Core map/filter/reduce/chunk/pluck/groupBy | **done** | M4 |
| `reject` / `where` / `firstWhere` | **done** | deepen |
| `merge` / `concat` / `collapse` / `flatten` | **done** | deepen |
| `partition` / `keyBy` / keyed `pluck` | **done** | deepen |
| `min` / `max` / `every` / `sum(selector)` | **done** | deepen |
| `pop` / `shift` / `prepend` / `forget` / `search` | **done** | deepen |
| `slice` / `nth` / `pad` / `tap` / `whenEmpty` | **done** | deepen |
| `sole` / `diff` / `intersect` / `mapWithKeys` / `countBy` | **done** | deepen |
| `times` / `wrap` / `join` | **done** | deepen |
| Lazy collections / higher-order proxies | **deferred** | intentional |

## 2. Arr

| API | Status | Evidence |
|-----|--------|----------|
| `get` / `has` / `set` (dot) | **done** | M4 |
| `only` / `except` / `wrap` / `flatten` | **done** | M4 |
| `forget` (dot) | **done** | deepen |
| `pluck` / `dot` | **done** | deepen |

## 3. Str / Stringable

| API | Status | Evidence |
|-----|--------|----------|
| Case / slug / limit / contains | **done** | M4 |
| `beforeLast` / `afterLast` / `between` | **done** | deepen |
| `headline` / `mask` / `substr` / `length` | **done** | deepen |
| `repeat` / `padLeft` / `padRight` | **done** | deepen |

## 4. KonaDate

| API | Status | Evidence |
|-----|--------|----------|
| now/parse/addDays/diffInSeconds | **done** | M4 |
| add/sub weeks/months/years | **done** | deepen |
| `startOfDay` / `endOfDay` / `setTimezone` | **done** | deepen |
| `isSameDay` / `diffInDays` | **done** | deepen |
| Full Carbon locale/human diff | **deferred** | intentional |

## 5. Public docs

| Page | Status |
|------|--------|
| Digging deeper → Support utilities | **done** (expanded) |

## Closure log

| Date | Notes |
|------|-------|
| 2026-10-16 | Collection/Arr/Str/Date deepen + Java regressions + 100% kover |
