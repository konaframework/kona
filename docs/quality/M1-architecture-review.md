# M1 architecture quality review

Source: [M1 architecture quality review](6b11b4a1-f9c2-4ab3-9661-b6665bfb8ba6) (Claude Opus).  
Status: backlog for M1 exhaustion before merge. Formal Laravel/Almasix parity inspection remains a separate merge gate.

## Headline

M1 is well tested, but thinner than the checklist claims. Several “done” items do not yet match Laravel behavior. Public API shapes that will break callers must be fixed **before** M1 merge.

## Bugfixes already applied (from review)

- Provider boot index-loop (providers registered during `boot()` also boot)
- `grind new` strips `.txt` and uses folder name for `APP_NAME`
- `grind make:controller` rejects path traversal / invalid class names
- `grind serve` reads `HOST`/`PORT` after `.env` load

## P0 — must fix before merge

1. Default `APP_DEBUG` to **false** (skeleton `.env` may keep `true`)
2. `Env`: real environment wins over `.env`; parse quotes / `export` / comments
3. Default `grind serve` host to **127.0.0.1**
4. Container thread-safety + circular dependency detection (`ReentrantLock`)
5. Request body size limit (413)
6. Path param URL-decoding; 405 with `Allow`; HEAD on GET routes; trailing-slash policy
7. Case-insensitive / multi-value response headers
8. `-Xjvm-default=all` + `Handler` / fluent Java APIs before freezing public surface
9. `grind new` must produce a buildable, servable app; `grind serve` must run it
10. Capture parity mapping as tests (see checklist item)

## Non-goals for M1

Netty engine, route trie, KSP discovery, sessions/auth/Crema, full validation rule set, Kover threshold changes, GitHub rulesets.
