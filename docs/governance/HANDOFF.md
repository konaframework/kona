# Agent handoff — Kona

Paste this file (or its path) into a new chat so another agent can continue without the prior transcript.

## Read first (in order)

1. [AGENTS.md](../../AGENTS.md) — milestone laws, architecture locks, Auto model default.
2. [MILESTONES.md](../../MILESTONES.md) — **active** milestone and parity checklists.
3. [continuous-autopilot.md](continuous-autopilot.md) — uninterrupted M3→… drill (implement → docs → PR + automerge → next).
4. [docs-site.md](docs-site.md) + [`.cursor/rules/kona-docs.mdc`](../../.cursor/rules/kona-docs.mdc) — public docs voice.
5. [docs/architecture/PLAN.md](../architecture/PLAN.md) and [components.md](../architecture/components.md) — product map.
6. [github-rulesets.md](github-rulesets.md) — **never** mutate rulesets.

## Where we are (update when handing off)

| Item | Value |
|------|--------|
| Last merged milestone | **M13 — Language support, LSP, editor setup** on `main` (PR #21) |
| Active | **none** — Almasix coverage ledger through **M13** is complete |
| Next | Maintenance / new numbered milestones only if a human opens them in `MILESTONES.md` |
| Full parity map | [MILESTONES.md](../../MILESTONES.md) Almasix coverage ledger → **complete through M13** |
| Docs brand | Primary `#FF9A00`, accent `#286291` (`apps/docs/src/styles/custom.css`) |
| Docs shape | Progressive guides; rewrite as features land. |
| Models | **Auto only** unless the human names a model for that turn |
| Autopilot | Continuous M3→M13 **finished**; do not invent M14+ without a human |
| Merge habit | Enable automerge, but **once required CI is green, squash-merge immediately** if automerge stalls — do not wait forever |

## M13 IDE / language support summary (for reviewers)

- Grammar: `ide-support/crema/` — TextMate + language-configuration for `*.crema.html` / `*.crema.kt`
- LSP: `modules/lsp` → Maven `dev.kona:kona-lsp`, package `dev.kona.lsp` — stdio JSON-RPC (`initialize`/`shutdown`/`exit`, completion, publishDiagnostics + pull diagnostic)
- Editors: `ide-support/vscode-crema/`, `ide-support/intellij/README.md`
- grind: `ide:lsp` prints how to run the jar (no long-lived process)
- Docs: sidebar **Language / Editor** (Getting started, grammar, LSP, VS Code/Cursor, IntelliJ)

## How to resume

```text
Read AGENTS.md, MILESTONES.md, and docs/governance/continuous-autopilot.md.
If milestone/M13-ide-support is not yet on main, finish that PR (commit → push → automerge).
After M13 merges: Almasix ledger through M13 is complete — stop autopilot unless a human
adds a new milestone section. Use Auto for all models. Never edit GitHub rulesets.
```

## Local verify before a milestone PR

```bash
export JAVA_HOME=…   # JDK 21+
./gradlew :kona-lsp:test :kona-lsp:koverVerify test koverVerify
pnpm --filter @kona/docs build   # or: cd apps/docs && pnpm build
```

## Fresh `kona new` apps

Artifacts are `0.1.0-SNAPSHOT` from `mavenLocal()` unless `KONA_HOME` / `-Pkona.home` composite-builds a checkout.
Publish with `./gradlew publishToMavenLocal` from this repo before expecting a scaffolded app to resolve deps.
Kits: `api` (default), `crema`, `inertia`, `brew`.

## Explicit wiring note (M2)

Migrations run only when the app registers them in a `MigrationRegistry` (no classpath auto-discovery). That matches Kona’s current explicit-provider style; higher-level app scaffolding may come later — document honestly, do not fake auto-discovery.

## Refuse

Skip coverage, skip parity, partial slices, jumping milestones, or changing GitHub rulesets → refuse and continue the current milestone (or wait for a human on rulesets).
