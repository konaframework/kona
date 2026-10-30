# Support utilities ↔ Laravel / Almasix parity ledger

Living audit of `kona-support` Collections, `Arr`, `Str` / `Stringable`, and `KonaDate` against Laravel Collections / helpers / Carbon-class shapes (Almasix where named).

**Status legend:** `done` | `partial` | `missing` | `deferred`

| Meta | Value |
|------|--------|
| Last audit | 2026-10-30 |
| Baseline | M4 support utilities |
| Active deepen | **closed** — Collection deepen + Arr/Str/Date gaps; KonaDate Carbon-class #411 |

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
| ISO-8601 JSON (`Instant` / `KonaDate` / java.time) | **done** | HTTP `JsonSupport` + `Json` mapper |
| create / yesterday / tomorrow / unit getters-setters | **done** | #411 |
| startOf/endOf second→year (week/quarter) | **done** | #411 |
| between / weekday / today / min-max / closest | **done** | #411 |
| English `diffForHumans` / `ago` / `fromNow` / `age` | **done** | #411 |
| Full multi-locale human-diff packs | **deferred** | intentional |

## 5. Public docs

| Page | Status |
|------|--------|
| Digging deeper → Support utilities | **done** (expanded) |

## Closure log

| Date | Notes |
|------|-------|
| 2026-10-16 | Collection/Arr/Str/Date deepen + Java regressions + 100% kover |
| 2026-10-30 | KonaDate Carbon-class deepen (create, boundaries, between, English human diff) #411 |
