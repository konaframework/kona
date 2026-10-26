# Agent handoff — Kona

Paste this file (or its path) into a new chat so another agent can continue without the prior transcript.

## Purpose

**Kona** is a from-scratch JVM web framework (`org.konaframework`): Kotlin-first internals, Java first-class public API, Virtual Threads, Netty HTTP, Cadence ORM, Crema templates, Grind Console, drip HTTP client, steam scheduler. Org: [konaframework](https://github.com/konaframework). Public docs: https://docs.konaframework.org/

## Read first (in order)

1. [AGENTS.md](../../AGENTS.md) — milestone laws, architecture locks, Auto model default.
2. [MILESTONES.md](../../MILESTONES.md) — **active** milestone and Almasix parity ledger.
3. [continuous-autopilot.md](continuous-autopilot.md) — uninterrupted drill (implement → docs → PR + automerge; non-colliding milestones may run in parallel).
4. [docs-site.md](docs-site.md) + [`.cursor/rules/kona-docs.mdc`](../../.cursor/rules/kona-docs.mdc) — public docs voice (feature-guide depth; Getting Started stays simple).
5. [docs/architecture/PLAN.md](../architecture/PLAN.md) and [components.md](../architecture/components.md) — product map.
6. [github-rulesets.md](github-rulesets.md) — **never** mutate rulesets (Law 9).
7. [publishing.md](publishing.md) — Maven Local today; Maven Central prerequisites and blockers for `org.konaframework`.
8. Sister IDE repo: [`konaframework/kona-idea` → `HANDOFF.md`](https://github.com/konaframework/kona-idea/blob/main/HANDOFF.md).

## In-repo Wire / Inertia (remonorepo)

| Product | Module | Docs | Public URL |
|---------|--------|------|------------|
| Wire (M26, train **0.3.0**; Brew aliases) | `modules/wire` → `:kona-wire` | `apps/docs` (Frontend → Wire) | https://docs.konaframework.org/docs/wire/ |
| Inertia | `modules/inertia` → `:kona-inertia` | `apps/docs` (Frontend → Inertia) | https://docs.konaframework.org/docs/inertia/ |
| Firefly support (M28, train **0.4.x**) | `modules/firefly-support` → `:kona-firefly-support` | `apps/docs` (Firefly) | https://docs.konaframework.org/docs/firefly/ |
| Firefly schemas (M29, train **0.4.x**) | `modules/firefly-schemas` → `:kona-firefly-schemas` | `apps/docs` (Firefly → Schemas) | https://docs.konaframework.org/docs/firefly/schemas/ |
| Wire JS | `js/wire/` | — | bundled into `kona-wire` resources |

Stub only: `apps/wire-docs/README.md`, `apps/inertia-docs/README.md`. **Archived** sister repos: `konaframework/kona-brew`, `konaframework/kona-inertia`.

Other product repo: sister IDE [`konaframework/kona-idea`](https://github.com/konaframework/kona-idea). Do **not** put handoff in `~/hello-kona`.

## Where we are (2026-10-26)

| Item | Value |
|------|--------|
| Default branch | `main` — green; never push directly |
| Repo version | `kona.version` **0.3.0**. M26 (Kona Wire) has shipped on that train. Do not tag **0.4.x** unless a human asks. |
| Active milestone | **M29 — Firefly schemas** (`kona-firefly-schemas`: schema tree, layouts, primes, Get/Set, static-or-closure setters). Release train **0.4.x**. Not merged yet. |
| Queued | **M30+** after M29 is on `main`. **M27 — Cadence Document / MongoDB** stays Kona **1.x** and human-gated. |
| Ledgers | Cadence / Security / HTTP / Queue-Schedule / Support deepen batches **closed** (#87–#91 + governance #92) |
| App layout | Default `kona new` is **web** (Crema welcome); `--kit=api` / `--kit=inertia` / Wire starter via package |
| Docs brand | Light caramel `#DF8723` / accent `#74453F`; dark latte `#C9A57B`; cream headings `#E8D4B8` |
| Docs versions | `starlight-versions`: **`{major}.x (Latest)`** (default current from `kona.version`), **Dev** (`main` tip archive) |
| Docs i18n | Root English; locales `es` / `fr` / `zh-cn` (Introduction + Installation translated; else EN fallback) |
| LSP | `kona-lsp` completes Crema `@`, Wire `wire:*`, and echo helpers inside `{{ }}` (IDEA-aligned `CremaEchoCatalog`) |
| Models | **Auto only** unless the human names a model for that turn |
| Autopilot | Non-colliding milestones may proceed in parallel. After a merge, continue remaining milestones whose dependencies are on `main`, including ones already in progress. Do not pause for permission |
| Merge habit | Enable automerge; squash-merge when CI green if automerge stalls |
| Branch protection | Never push to `main`; never mutate GitHub rulesets (Law 9) |

### Recently merged (this session / adjacent)

| Work | PR |
|------|-----|
| Grind `route:list` / `route:cache` / `route:clear` | *(this PR — `feat/grind-route-commands`)* |
| Docs versions + i18n + LSP `wire:*` + GH chip hydrate | *(this PR — `feat/docs-ide-i18n-versions`)* |
| Remonorepo Wire + Inertia into `modules/{brew,inertia}` | [#138](https://github.com/konaframework/kona/pull/138) (landed; supersedes #137 approach) |
| Handoff / publishing Central notes | [#135](https://github.com/konaframework/kona/pull/135) |
| Extract `kona-inertia` + brief docs link-out | [#101](https://github.com/konaframework/kona/pull/101) |
| `./grind install` / `kona grind install` + provider registration | [#100](https://github.com/konaframework/kona/pull/100); top-level `kona install` removed |
| Exhaust Testing section | [#99](https://github.com/konaframework/kona/pull/99) |
| Logging drivers (stack, daily, …) + exhaustive docs | [#98](https://github.com/konaframework/kona/pull/98) |
| Expand Grind Console guides | [#97](https://github.com/konaframework/kona/pull/97) |
| Exhaust Events into dedicated section | [#96](https://github.com/konaframework/kona/pull/96) |
| Crema `view()` / Views helper (share, first) + docs | [#95](https://github.com/konaframework/kona/pull/95) |
| Shrink Wire section to brief link-out | [#94](https://github.com/konaframework/kona/pull/94) |
| Exhaust HTTP + Crema feature guides | [#93](https://github.com/konaframework/kona/pull/93) |
| Close deepen ledger status after #88–#91 | [#92](https://github.com/konaframework/kona/pull/92) |
| Support Collections/Arr/Str/Date deepen | [#91](https://github.com/konaframework/kona/pull/91) |
| Queue full batches | [#90](https://github.com/konaframework/kona/pull/90) |
| HTTP niche validation / stream / controller middleware | [#89](https://github.com/konaframework/kona/pull/89) |
| Security CadenceUserProvider + Argon2 | [#88](https://github.com/konaframework/kona/pull/88) |
| Cadence B6 Eloquent parity | [#87](https://github.com/konaframework/kona/pull/87) |
| Wire cutover to external `kona-wire` | [#79](https://github.com/konaframework/kona/pull/79) |

### Done vs in-flight (honest)

**Done on `main`:** logging drivers, `view()` / Views, Grind Console docs, Events / Testing sections, Wire/Inertia brief link-outs, `./grind install` / `kona grind install`, first `0.1.0` release train tagged on kona (#134). Continuous autopilot + Auto model locks remain in AGENTS / rules.

**Remonorepo done (#138):** Wire + Inertia are in-tree (`modules/wire`, `modules/inertia`, samples, `js/wire`). Docs unified into `apps/docs` (Frontend → Wire / Inertia). **Human Cloudflare follow-up:** redirect `brew.konaframework.org` → `/docs/wire/`, `inertia.konaframework.org` → `/docs/inertia/`; retire Workers `kona-wire-docs` / `kona-inertia-docs` (see [docs-site.md](docs-site.md)).

**Repo version:** `gradle.properties` `kona.version` is **0.3.0** (M26). Confirm Maven Central before describing a version as downloadable. Do not tag Firefly **0.4.x** unless a human asks. Sister IDE: [`kona-idea`](https://github.com/konaframework/kona-idea) latest tag tracked in that repo’s `HANDOFF.md`.

**Recently closed:** [#150](https://github.com/konaframework/kona/issues/150) grind/Cadence/Wire parity — `make:model` `-mcrfspaR` / `--api` / `--requests` companions (#151 + seal PR), `META-INF/kona/migrations` + `MigrationDiscovery` auto-register, schema `longText`/`json`, quiet grind launcher + serve banner (#152), `@CremaTemplate` / `// language=Crema` for inline Wire templates.

**Scaffold indent:** `kona new` and `./grind install inertia` emit 2-space `package.json` / `tsconfig.json` via `PrettyJson` and assemble `vite.config.js` / Wire welcome HTML with `SourceBlocks` so nested `trimIndent()` cannot collapse blocks to column 0. Sister IDE **kona-idea 0.5.20** lists those frontend files in the Kona Project View.

### Intentional deferrals (do not reopen without mandate)

- Horizon / queue metrics dashboard
- Cross-process batch `then`/`catch` callbacks (durable counts only)
- Lazy collections / higher-order proxies
- Full Carbon locale / human-diff surface
- ~~`make:migration` auto-register into `MigrationRegistry`~~ — done (`META-INF/kona/migrations` + `MigrationDiscovery`; see #150)
- Pivot / morph-pivot `make:model` stubs — defer until Cadence pivot model surface is first-class

### Notes

- Do **not** revive Extract names — ORM is **Cadence**.
- **CLI lock:** user-facing terminal output uses Termwind-style `ConsoleIO` (`kona-support`); grind wraps it as `GrindIO`. No raw `println`.
- Wire and Inertia exhaustive guides live under `apps/docs` sidebar **Frontend → Wire / Inertia** (not separate sites).

## How to continue

```bash
# Sync
git fetch origin && git checkout main && git pull

# Full test + 100% coverage gate
./gradlew test koverVerify

# Scaffold
./gradlew :kona-cli:run --args='new demo --lang=kotlin'
# or global CLI after install: kona new demo --lang=kotlin

# Docs (local)
pnpm install --frozen-lockfile && pnpm docs:dev          # apps/docs (includes Wire + Inertia)

# Publish all jars incl. brew/inertia
./gradlew publishToMavenLocal -x test
# Full maintainer guide: docs/governance/publishing.md
```

Key paths: `modules/` (framework), `apps/docs/`, `js/wire/`, `docs/governance/`, `.cursor/rules/kona-*.mdc`.

## Open gaps / next priorities

1. Cloudflare: redirect/retire `brew.konaframework.org` / `inertia.konaframework.org` (see docs-site.md).
2. Confirm no conflicting open PRs.
3. Pick remaining **deepen** slices from the Almasix ledger. Non-colliding milestones may proceed in parallel, each on its own branch. Do not start a milestone that depends on another until that dependency is on `main`. **M26 Wire** shipped on **0.3.0**.
4. **Maven Central — `v0.2.0` shipped** (do not re-tag). **`0.2.1` version bump on `main` — do not tag until release cut.** Optional follow-ups:
   - File Count monthly limit (grace through **2026-11-17**): check Usage Center; request higher limit if needed. Checksum-of-`.asc` reduction needs Gradle 9 + vanniktech ≥0.37 (deferred).
   - Trusted Publishing (OIDC) still **not GA** — token + GPG. Full checklist: [publishing.md](publishing.md).

## Do not

- Skip coverage or parity; run parallel work that collides or depends on a milestone that is not yet on `main`; embed Quarkus/Micronaut/Spring.
- Mutate GitHub rulesets.
- Pin named Cursor models unless the human asks.
- Commit `~/hello-kona` or treat it as a product repo.
