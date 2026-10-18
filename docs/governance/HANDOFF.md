# Agent handoff — Kona

Paste this file (or its path) into a new chat so another agent can continue without the prior transcript.

## Purpose

**Kona** is a from-scratch JVM web framework (`org.konaframework`): Kotlin-first internals, Java first-class public API, Virtual Threads, Netty HTTP, Cadence ORM, Crema templates, Grind Console, drip HTTP client, steam scheduler. Org: [konaframework](https://github.com/konaframework). Public docs: https://docs.konaframework.org/

## Read first (in order)

1. [AGENTS.md](../../AGENTS.md) — milestone laws, architecture locks, Auto model default.
2. [MILESTONES.md](../../MILESTONES.md) — **active** milestone and Almasix parity ledger.
3. [continuous-autopilot.md](continuous-autopilot.md) — uninterrupted drill (implement → docs → PR + automerge → next).
4. [docs-site.md](docs-site.md) + [`.cursor/rules/kona-docs.mdc`](../../.cursor/rules/kona-docs.mdc) — public docs voice (feature-guide depth; Getting Started stays simple).
5. [docs/architecture/PLAN.md](../architecture/PLAN.md) and [components.md](../architecture/components.md) — product map.
6. [github-rulesets.md](github-rulesets.md) — **never** mutate rulesets (Law 9).
7. [publishing.md](publishing.md) — Maven Local today; Maven Central prerequisites and blockers for `org.konaframework`.
8. Sister IDE repo: [`konaframework/kona-idea` → `HANDOFF.md`](https://github.com/konaframework/kona-idea/blob/main/HANDOFF.md).

## In-repo Brew / Inertia (remonorepo)

| Product | Module | Docs app | Public site |
|---------|--------|----------|-------------|
| Brew | `modules/brew` → `:kona-brew` | `apps/brew-docs` | https://brew.konaframework.org/ |
| Inertia | `modules/inertia` → `:kona-inertia` | `apps/inertia-docs` | https://inertia.konaframework.org/ |
| Brew JS | `js/brew/` | — | bundled into `kona-brew` resources |

To archive after remonorepo merges (do not publish from; delete later): `konaframework/kona-brew`, `konaframework/kona-inertia`. Local checkouts `~/kona-brew` / `~/kona-inertia` are historical only.

Other product repo: sister IDE [`konaframework/kona-idea`](https://github.com/konaframework/kona-idea). Do **not** put handoff in `~/hello-kona`.

## Where we are (2026-10-18)

| Item | Value |
|------|--------|
| Default branch | `main` — green; never push directly |
| Last milestone coding | **M24 — Executable config** (PR #53); deepen batches after that closed via #87–#91 |
| Active milestone | **none** — scan Almasix ledger / intentional deferrals; open next deepen only with a clear checklist |
| Ledgers | Cadence / Security / HTTP / Queue-Schedule / Support deepen batches **closed** (#87–#91 + governance #92) |
| App layout | Default `kona new` is **web** (Crema welcome); `--kit=api` / `--kit=inertia` / Brew starter via package |
| Docs brand | Light caramel `#DF8723` / accent `#74453F`; dark latte `#C9A57B`; cream headings `#E8D4B8` |
| Models | **Auto only** unless the human names a model for that turn |
| Autopilot | Continue deepen / docs exhaustion; do not pause between merges |
| Merge habit | Enable automerge; squash-merge when CI green if automerge stalls |
| Branch protection | Never push to `main`; never mutate GitHub rulesets (Law 9) |

### Recently merged (this session / adjacent)

| Work | PR |
|------|-----|
| Remonorepo Brew + Inertia into `modules/{brew,inertia}` | [#138](https://github.com/konaframework/kona/pull/138) (reverts #137 sister-CI decoupling) |
| Handoff / publishing Central notes | [#135](https://github.com/konaframework/kona/pull/135) |
| Extract `kona-inertia` + brief docs link-out | [#101](https://github.com/konaframework/kona/pull/101) |
| `./grind install` / `kona grind install` + provider registration | [#100](https://github.com/konaframework/kona/pull/100); top-level `kona install` removed |
| Exhaust Testing section | [#99](https://github.com/konaframework/kona/pull/99) |
| Logging drivers (stack, daily, …) + exhaustive docs | [#98](https://github.com/konaframework/kona/pull/98) |
| Expand Grind Console guides | [#97](https://github.com/konaframework/kona/pull/97) |
| Exhaust Events into dedicated section | [#96](https://github.com/konaframework/kona/pull/96) |
| Crema `view()` / Views helper (share, first) + docs | [#95](https://github.com/konaframework/kona/pull/95) |
| Shrink Brew section to brief link-out | [#94](https://github.com/konaframework/kona/pull/94) |
| Exhaust HTTP + Crema feature guides | [#93](https://github.com/konaframework/kona/pull/93) |
| Close deepen ledger status after #88–#91 | [#92](https://github.com/konaframework/kona/pull/92) |
| Support Collections/Arr/Str/Date deepen | [#91](https://github.com/konaframework/kona/pull/91) |
| Queue full batches | [#90](https://github.com/konaframework/kona/pull/90) |
| HTTP niche validation / stream / controller middleware | [#89](https://github.com/konaframework/kona/pull/89) |
| Security CadenceUserProvider + Argon2 | [#88](https://github.com/konaframework/kona/pull/88) |
| Cadence B6 Eloquent parity | [#87](https://github.com/konaframework/kona/pull/87) |
| Brew cutover to external `kona-brew` | [#79](https://github.com/konaframework/kona/pull/79) |

### Done vs in-flight (honest)

**Done on `main`:** logging drivers, `view()` / Views, Grind Console docs, Events / Testing sections, Brew/Inertia brief link-outs, `./grind install` / `kona grind install`, first `0.1.0` release train tagged on kona (#134). Continuous autopilot + Auto model locks remain in AGENTS / rules.

**Remonorepo (in progress / this handoff):** Brew + Inertia return to this monorepo (`modules/brew`, `modules/inertia`, samples, `apps/brew-docs`, `apps/inertia-docs`, `js/brew`). Close obsolete #137 (decouple-sister-ci) as superseded. Archive sister GitHub repos after remonorepo lands. **Human Cloudflare follow-up:** point `kona-brew-docs` / `kona-inertia-docs` Workers Builds at `konaframework/kona` with root `apps/brew-docs` / `apps/inertia-docs`.

### Intentional deferrals (do not reopen without mandate)

- Horizon / queue metrics dashboard
- Cross-process batch `then`/`catch` callbacks (durable counts only)
- Lazy collections / higher-order proxies
- Full Carbon locale / human-diff surface
- `make:migration` auto-register into `MigrationRegistry` (generator still prints a manual bind hint)

### Notes

- Do **not** revive Extract names — ORM is **Cadence**.
- **CLI lock:** user-facing terminal output uses Termwind-style `ConsoleIO` (`kona-support`); grind wraps it as `GrindIO`. No raw `println`.
- Brew and Inertia exhaustive guides stay on separate Starlight sites (`apps/brew-docs`, `apps/inertia-docs`); Kona `apps/docs` keeps brief link-outs.

## How to continue

```bash
# Sync
git fetch origin && git checkout main && git pull

# Full test + 100% coverage gate
./gradlew test koverVerify

# Scaffold
./gradlew :kona-cli:run --args='new demo --lang=kotlin'
# or global CLI after install: kona new demo --lang=kotlin

# Docs sites (local)
pnpm install --frozen-lockfile && pnpm docs:dev          # apps/docs
(cd apps/brew-docs && npm ci && npm run dev)             # brew
(cd apps/inertia-docs && npm ci && npm run dev)          # inertia

# Publish all jars incl. brew/inertia
./gradlew publishToMavenLocal -x test
# Full maintainer guide: docs/governance/publishing.md
```

Key paths: `modules/` (framework), `apps/docs|brew-docs|inertia-docs/`, `js/brew/`, `docs/governance/`, `.cursor/rules/kona-*.mdc`.

## Open gaps / next priorities

1. Land remonorepo PR → archive sister repos → Cloudflare dashboard root-path update (see docs-site.md).
2. Confirm no conflicting open PRs; #137 closed as superseded.
3. Pick the next **deepen** slice from remaining Almasix ledger rows — one milestone branch at a time.
4. **Maven Central (first 0.1.0 train — 2026-10-18):**
   - **kona** tag `v0.1.0` release workflow uploaded; **Human blocker:** Portal **Publish** when `VALIDATED`.
   - After remonorepo, brew/inertia publish with the **same** kona tag train (no separate sister tags).
   - Trusted Publishing (OIDC) still **not GA** — token + GPG.

## Do not

- Skip coverage or parity; jump milestones; embed Quarkus/Micronaut/Spring.
- Mutate GitHub rulesets.
- Pin named Cursor models unless the human asks.
- Commit `~/hello-kona` or treat it as a product repo.
