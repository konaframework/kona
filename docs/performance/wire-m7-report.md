# Performance report (M7)

Follow-up to [baseline.md](baseline.md). Same machine class: JDK 21, Linux (WSL2).

## Server (`./gradlew :kona-wire:bench`)

Hot paths remain in the same band as M1:

| Path | Time per op |
|------|-------------|
| Mount counter | ~8 µs |
| Update counter | ~7–9 µs |
| Mount 100-row keyed list | ~0.5–0.6 ms |
| Update 100-row keyed list | ~0.5–0.6 ms |

Uploads (M6) add signed HMAC + disk write; typical local `storeUpload` of a 4 KiB body is sub-millisecond on SSD.

## Concurrent load

Running 32 virtual threads each performing 1_000 counter updates against an in-process `WireManager`
completes without checksum failures. Throughput is dominated by Crema render cost, not Wire snapshot HMAC.

## Chromium (Playwright smoke)

`cd js && npx playwright test` boots `brew.min.js` in Chromium, mounts a snapshot root, and asserts
`$wire` is present. Cold load of the min bundle is well under 100 ms on a local machine; treat CI
numbers as smoke, not a micro-benchmark.

## Bundle sizes

See baseline.md — CSP build (`brew.csp.min.js`) is the Alpine-CSP safe variant selected when
`brew.csp_safe=true`.
