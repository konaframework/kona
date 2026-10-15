# Agent handoff — Kona

Paste this file (or its path) into a new chat so another agent can continue without the prior transcript.

## Read first (in order)

1. [AGENTS.md](../../AGENTS.md) — milestone laws, architecture locks, Auto model default.
2. [MILESTONES.md](../../MILESTONES.md) — **active** milestone and parity checklists.
3. [continuous-autopilot.md](continuous-autopilot.md) — uninterrupted M3→… drill (implement → docs → PR + automerge → next).
4. [docs-site.md](docs-site.md) + [`.cursor/rules/kona-docs.mdc`](../../.cursor/rules/kona-docs.mdc) — public docs voice.
5. [docs/architecture/PLAN.md](../architecture/PLAN.md) and [components.md](../architecture/components.md) — product map.
6. [github-rulesets.md](github-rulesets.md) — **never** mutate rulesets.
7. Sister IDE repo handoff: [`konaframework/kona-idea` → `HANDOFF.md`](https://github.com/konaframework/kona-idea/blob/main/HANDOFF.md).

## Where we are (update when handing off)

| Item | Value |
|------|--------|
| Last merged milestone | **M13 — Language support, LSP, editor setup** on `main` (PR #21) |
| Active | **none** — Almasix coverage ledger through **M13** is complete |
| Next | Maintenance / focused deepen **only if a human opens a milestone** in `MILESTONES.md` |
| Full parity map | [MILESTONES.md](../../MILESTONES.md) Almasix coverage ledger → **complete through M13** |
| Docs brand | Primary `#FF9A00`, accent `#286291` (`apps/docs/src/styles/custom.css`) |
| Docs shape | Progressive guides; rewrite as features land. |
| Models | **Auto only** unless the human names a model for that turn |
| Autopilot | Continuous M3→M13 **finished**; do not invent M14+ without a human |
| Merge habit | Enable automerge; **once required CI is green, squash-merge immediately** if automerge stalls. PRs that only touch `docs/governance` may skip **Build site** — touch `apps/docs/` so that required check runs. |
| Branch protection | Never push to `main`; never mutate GitHub rulesets (Law 9). |

### Recently merged (post-M13)

| Work | PR / location |
|------|----------------|
| Post-M13 kit classpath + serve regressions | PR #23 |
| Crema Blade/Prism app-facing directives + docs | PR #24 |
| Extract Eloquent-shaped deepen + docs | PR #25 |
| Docs → native IntelliJ plugin | PR #26 |
| HANDOFF thin-surface audit notes | PR #27 |
| Native IntelliJ plugin (separate repo) | [`konaframework/kona-idea`](https://github.com/konaframework/kona-idea) `v0.1.0` |

| Item | Value |
|------|--------|
| Post-M13 sweep | Full `test`+`koverVerify` green; hello samples + `kona new` kits (api/crema/brew/inertia) HTTP smoke; kit regressions in `GeneratedAppBuildTest`. |
| Crema parity | App-facing Blade/Prism surface on `main` (#24). Docs under Crema sidebar + forms. Deferred: Vite manifest/hot, rich `<x-*>` attrs. |
| Extract parity | `firstOrCreate`/`updateOrCreate`, upserts, `whereHas`/`withCount`, scopes, pivot polish, chunk, quiet saves, accessor hooks (#25). Deferred: morph*/through, custom cast classes, cursor/simple pagination. |
| Kona Idea | Separate repo [`konaframework/kona-idea`](https://github.com/konaframework/kona-idea) (`dev.kona.idea`). Monorepo TextMate path is legacy. Keep `CremaDirectives` in sync with `modules/lsp` `CremaDirectives`. |

## Known thin surfaces (ledger “done”, deepen later)

Several M3–M10 checklist items are **shape-complete but behaviorally thin**. Do **not** invent M14+ unless a human opens a milestone.

| Area | Thinness |
|------|----------|
| Scaffold / `kona new` | Some kits need manual provider/deps for queue/schedule/mail/session/auth; grind `queue:*` / `schedule:*` fail if unbound |
| Queue | Job payload not fully cross-process (`JobRegistry` in-process); `release` delay ignored; failed jobs in-memory; `queue:retry` forgets without re-dispatch |
| Notifications / broadcast / search | “Database” notifications in-memory; broadcast = log/null; search = collection/memory only |
| Session / auth | Redis session store may not be injected by `SessionServiceProvider`; cookie session unsigned; users/tokens/password resets often in-memory |
| Cache / Redis / support | No cache locks/tags/increment; Redis client surface minimal; no lang-file loader |
| Events / mail | Sync bus only; SMTP minimal (no attachments / queued mailables) |
| Testing / drip | HTTP client get+postJson; fakes incomplete; drip no patch/multipart |
| Crema / Extract | Deepen landed #24/#25 — remaining: morphs/through, Vite manifest/hot, rich `<x-*>` |

**Preferred deepen order** (when a human opens a milestone): (1) durable queue serialize + retry, (2) scaffold provider wiring, (3) Redis session wire-up + signed cookies, (4) durable notification/failed-job stores.

## Sister project — Kona Idea

| | |
|--|--|
| Repo | https://github.com/konaframework/kona-idea |
| Local clone (this machine) | `/home/smaosa/Projects/kona-idea` |
| Plugin id | `dev.kona.idea` |
| Latest release | [v0.1.0](https://github.com/konaframework/kona-idea/releases/tag/v0.1.0) (`kona-idea-0.1.0.zip`) |
| Handoff | [HANDOFF.md](https://github.com/konaframework/kona-idea/blob/main/HANDOFF.md) in that repo |

When adding Crema directives in `modules/lsp` / `CremaRuntime`, update `dev.kona.idea.CremaDirectives` in kona-idea and cut a patch release.

## M13 IDE / language support summary

- Grammar: `ide-support/crema/` — TextMate + language-configuration for `*.crema.html` / `*.crema.kt`
- LSP: `modules/lsp` → `dev.kona:kona-lsp` — stdio JSON-RPC
- Editors: `ide-support/vscode-crema/`; native IntelliJ → kona-idea; legacy notes `ide-support/intellij/README.md`
- grind: `ide:lsp` prints jar run reminder (no long-lived process)
- Docs: sidebar **Language / Editor**

## How to resume

```text
Read AGENTS.md, MILESTONES.md, continuous-autopilot.md, and this HANDOFF.
Active milestone is none. Do not invent M14+ without a human editing MILESTONES.md.
For IntelliJ plugin work, open konaframework/kona-idea (see its HANDOFF.md).
Use Auto for all models. Never edit GitHub rulesets.
Ship via feature branch → PR → green CI → squash merge (automerge + force-merge if stalled).
```

## Local verify before a PR

```bash
export JAVA_HOME=…   # JDK 21+
./gradlew test koverVerify
# If apps/docs/ changed:
pnpm --filter @kona/docs build   # or: cd apps/docs && pnpm build
```

## Fresh `kona new` apps

Artifacts are `0.1.0-SNAPSHOT` from `mavenLocal()` unless `KONA_HOME` / `-Pkona.home` composite-builds a checkout.
Publish with `./gradlew publishToMavenLocal` before expecting a scaffolded app to resolve deps.
Kits: `api` (default), `crema`, `inertia`, `brew`.

## Explicit wiring note (M2)

Migrations run only when the app registers them in a `MigrationRegistry` (no classpath auto-discovery). Document honestly; do not fake auto-discovery.

## Refuse

Skip coverage, skip parity, partial slices, jumping milestones, or changing GitHub rulesets → refuse and finish the current milestone (or wait for a human on rulesets).
