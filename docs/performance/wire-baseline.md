# Performance baseline (M1)

Recorded on the M1 branch: JDK 21 (Temurin), WSL2 Linux, single thread, after JIT warm-up. Use these numbers to
catch regressions; the M7 report repeats them in Chromium and under concurrent load.

## Server hot paths — `./gradlew :kona-wire:bench`

| Path | Time per op |
|------|-------------|
| Mount counter (hydrate defaults, render, dehydrate, checksum) | ~8 µs |
| Update counter (`increment`: verify checksum, hydrate, call, render, dehydrate) | ~7 µs |
| Mount 100-row keyed list | ~530 µs |
| Update 100-row keyed list (`toggle(5)`) | ~535 µs |

Breakdown of the 100-row list (`BREW_BENCH_PROFILE=1 ./gradlew :kona-wire:bench`):

| Part | Time per op |
|------|-------------|
| Crema render of the template | ~370–415 µs |
| Snapshot dehydration + HMAC checksum | ~45 µs |
| Root attribute injection | ~1.4 µs |

Crema interprets the template source on every render, so it dominates list rendering. Wire's own overhead
(reflection metadata is cached per class in `ClassValue`; no per-request scans) stays a small fraction. Compiled
template caching in Crema is the largest available win and is tracked on the Kona side.

Counter snapshot payload: 242 bytes.

## Client — `cd js && npm run bench`

Keyed morph of a list where one row changes, measured in jsdom (much slower than a real browser, so treat these
as relative numbers only):

| List | Mean per morph |
|------|----------------|
| 100 rows | ~6.6 ms |
| 1000 rows | ~94 ms |

## Bundle sizes

| File | Raw | Gzip |
|------|-----|------|
| `brew.min.js` (Alpine + 9 plugins + Wire) | 177 KB | 61 KB |
| `brew.js` (debug, unminified) | 420 KB | 92 KB |
| `brew.esm.js` (for app bundlers) | 396 KB | 91 KB |

The asset route serves `brew.min.js` with `Cache-Control: public, max-age=31536000, immutable` and a content-hash
`?id=` query string, so each browser downloads it once per release.
