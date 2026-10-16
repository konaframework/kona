# Filesystem / Storage parity ledger

Durable audit of **kona-filesystem** against Laravel Storage / Flysystem behavioral surface (and Almasix equivalents where named). Update checkboxes as gaps close. Public docs must not name Laravel/Almasix; this ledger is agent/governance only.

**Branch / work:** `feat/filesystem-storage-parity`  
**Worktree:** `/home/smaosa/kona-fs-parity` (isolated from concurrent agents)  
**Stay out of:** `modules/http`, `modules/cadence`, `modules/grind` (except tiny filesystem-only `make:` if needed), `modules/auth`, session  
**Modules in scope:** `kona-filesystem`, `kona-testing` (`FakeStorage` only as interface sync), `apps/docs/` (filesystem guides + sidebar)  
**HTTP cross-links only:** `ServePublicStorage`, signed downloads — already M23; document, do not deepen HTTP here.

## Surface matrix

| # | Surface | Status |
|---|---------|--------|
| F1 | Local disk enrich | done |
| F2 | Public disk | done |
| F3 | S3 + path `prefix` | done |
| F4 | Memory / s3-memory | done |
| F5 | Scoped disk | done |
| F6 | Read-only disk | done |
| F7 | Core CRUD | done |
| F8 | `missing` | done |
| F9 | Directories | done |
| F10 | `allFiles` / `allDirectories` | done |
| F11 | Metadata size/lastModified | done |
| F12 | `mimeType` | done |
| F13 | `checksum` | done |
| F14 | `path` / `url` | done |
| F15 | Temporary URLs + `buildTemporaryUrlsUsing` + `providesTemporaryUrls` | done |
| F16 | Visibility | done |
| F17 | Streams | done |
| F18 | Append / prepend | done |
| F19 | `putFile` / `putFileAs` | done |
| F20 | Cloud path prefix | done |
| F21 | `storage:link` (grind owned) | done |
| F22 | Fake disk + FakeStorage parity | done |
| F23 | Public serving / signed (M23 docs cross-link) | done |
| F24 | Java-callable API + regressions | done |
| F25 | Public docs depth | done |
| F26 | Coverage on in-scope types (HttpS3Client + Manager/S3 wire helpers excluded like prior M7) | done |

## Exit criteria

- [x] All rows above **done**
- [x] 100% line + branch Kover on verified production types (`LocalAdapter`, `ScopedAdapter`, `ReadOnlyAdapter`, …)
- [x] Java regressions for new public API
- [x] Docs under `apps/docs/` filesystem cluster
- [ ] PR merged to `main` with automerge — https://github.com/konaframework/kona/pull/67
- [x] Never mutate GitHub rulesets
