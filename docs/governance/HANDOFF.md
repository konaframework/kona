# Agent handoff — Kona

Paste this file (or its path) into a new chat so another agent can continue without the prior transcript.

## Purpose

**Kona** is a from-scratch JVM web framework (`io.konaframework`): Kotlin-first internals, Java first-class public API, Virtual Threads, Netty HTTP, Cadence ORM, Crema templates, Grind Console, drip HTTP client, steam scheduler. Org: [konaframework](https://github.com/konaframework). Public docs: https://konaframework.github.io/kona/

## Read first (in order)

1. [AGENTS.md](../../AGENTS.md) — milestone laws, architecture locks, Auto model default.
2. [MILESTONES.md](../../MILESTONES.md) — **active** milestone and Almasix parity ledger.
3. [continuous-autopilot.md](continuous-autopilot.md) — uninterrupted drill (implement → docs → PR + automerge → next).
4. [docs-site.md](docs-site.md) + [`.cursor/rules/kona-docs.mdc`](../../.cursor/rules/kona-docs.mdc) — public docs voice (feature-guide depth; Getting Started stays simple).
5. [docs/architecture/PLAN.md](../architecture/PLAN.md) and [components.md](../architecture/components.md) — product map.
6. [github-rulesets.md](github-rulesets.md) — **never** mutate rulesets (Law 9).
7. [publishing.md](publishing.md) — Maven Local today; Maven Central prerequisites and blockers for `io.konaframework`.
8. Sister IDE repo: [`konaframework/kona-idea` → `HANDOFF.md`](https://github.com/konaframework/kona-idea/blob/main/HANDOFF.md).

## Sibling product repos (this session)

| Repo | Path (sibling) | Docs | Handoff |
|------|----------------|------|---------|
| Brew (Livewire v4 parity) | `/home/smaosa/kona-brew` | https://konaframework.github.io/kona-brew/ | `docs/governance/HANDOFF.md` |
| Inertia | `/home/smaosa/kona-inertia` | https://konaframework.github.io/kona-inertia/ | `docs/governance/HANDOFF.md` |

Do **not** put handoff in `~/hello-kona` — throwaway `kona new` sample only.

Other `~/kona-*` directories (bench, netty, logging worktrees, etc.) are **not** product repos unless they have a real git remote under `konaframework`.

## Where we are (2026-10-16)

| Item | Value |
|------|--------|
| Default branch | `main` — green; never push directly |
| Last milestone coding | **M24 — Executable config** (PR #53); deepen batches after that closed via #87–#91 |
| Active milestone | **none** — scan Almasix ledger / intentional deferrals; open next deepen only with a clear checklist |
| Ledgers | Cadence / Security / HTTP / Queue-Schedule / Support deepen batches **closed** (#87–#91 + governance #92) |
| App layout | Default `kona new` is **web** (Crema welcome); `--kit=api` / `--kit=inertia` / Brew starter via package |
| Docs brand | Primary `#FF9A00`, accent `#286291` |
| Models | **Auto only** unless the human names a model for that turn |
| Autopilot | Continue deepen / docs exhaustion; do not pause between merges |
| Merge habit | Enable automerge; squash-merge when CI green if automerge stalls |
| Branch protection | Never push to `main`; never mutate GitHub rulesets (Law 9) |

### Recently merged (this session / adjacent)

| Work | PR |
|------|-----|
| Extract `kona-inertia` + brief docs link-out | [#101](https://github.com/konaframework/kona/pull/101) |
| `kona install [PACKAGE]` + provider registration | [#100](https://github.com/konaframework/kona/pull/100) |
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

**Done on `main`:** logging drivers, `view()` / Views, Grind Console docs rename/expansion, Events section, Testing section, Brew brief link-out, `kona install`, Inertia extracted to sibling repo (#101). Continuous autopilot + Auto model locks remain in AGENTS / rules.

**In-flight / local only (do not assume merged):**

- Untracked Brew logo/banner/mark SVGs under `apps/docs/public/branding/brew-*.svg` may appear in a dirty workspace (copied while Brew branding iterated). **Not on `main`.** Either discard or land a tiny docs PR that syncs marks with Brew if the link-out page needs them — do not mix into unrelated PRs.
- Further Kona docs exhaustion (Routing and other Basics) may still be running as parallel agents; check `gh pr list` before starting duplicate work.
- `~/hello-kona` recreate/`kona new` smoke tests are throwaway — ignore for product handoff.

### Intentional deferrals (do not reopen without mandate)

- Horizon / queue metrics dashboard
- Cross-process batch `then`/`catch` callbacks (durable counts only)
- Lazy collections / higher-order proxies
- Full Carbon locale / human-diff surface
- Brew implementation (lives in **kona-brew**; Kona keeps brief + starter/install wiring)
- Inertia implementation (lives in **kona-inertia**; Kona keeps brief + `--kit=inertia` skeletons)

### Notes

- Do **not** revive Extract names — ORM is **Cadence**.
- **CLI lock:** user-facing terminal output uses Termwind-style `ConsoleIO` (`kona-support`); grind wraps it as `GrindIO`. No raw `println`.
- Brew and Inertia exhaustive guides live on their own docs sites; Kona pages are brief link-outs only.

## How to continue

```bash
# Sync
git fetch origin && git checkout main && git pull

# Full test + 100% coverage gate
./gradlew test koverVerify

# Scaffold
./gradlew :kona-cli:run --args='new demo --lang=kotlin'
# or global CLI after install: kona new demo --lang=kotlin

# Docs site (local)
cd apps/docs && npm ci && npm run dev

# Publish for sibling packages (Brew / Inertia CI pattern)
./gradlew publishToMavenLocal -x test
# Full maintainer guide (Central blockers, order, versions): docs/governance/publishing.md
```

Key paths: `modules/` (framework), `apps/docs/` (public site), `docs/governance/` (laws + handoff), `.cursor/rules/kona-*.mdc`.

## Open gaps / next priorities

1. Confirm no open PRs; if docs-exhaustion PRs exist, finish those first.
2. Pick the next **deepen** slice from remaining Almasix ledger “implemented (checklist)” rows or human-requested gaps — one milestone branch at a time.
3. Keep sibling Brew/Inertia work in their repos; only touch Kona for install/starter/brief link-out.
4. Optionally sync Brew marks into Kona branding assets if the brief Brew page should show the espresso mark (see untracked `brew-*.svg` note above).
5. **Maven Central:** only `publishToMavenLocal` is wired — see [publishing.md](publishing.md). Do not public-publish until namespace, signing, POM metadata, and credentials exist.

## Do not

- Skip coverage or parity; jump milestones; embed Quarkus/Micronaut/Spring.
- Mutate GitHub rulesets.
- Pin named Cursor models unless the human asks.
- Commit `~/hello-kona` or treat it as a product repo.
