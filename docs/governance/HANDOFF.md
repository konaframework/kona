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
| Last merged milestone | **M2 — Extract** (`main`, PR #6) |
| Next / active | **M3 — Auth, session, cache** (`milestone/M3-auth-session-cache`) |
| Docs brand | Primary `#FF9A00`, accent `#286291` (`apps/docs/src/styles/custom.css`) |
| Docs shape | Still low-level (providers, explicit wiring). Trajectory: Laravel-style progressive guides (simple first, dig deeper later). Rewrite as features land — do not freeze the current structure. |
| Models | **Auto only** unless the human names a model for that turn |
| Autopilot | Continuous through remaining milestones; no pause between merges |

## Repo facts

- Org/repo: `konaframework/kona`
- Maven group: `dev.kona`
- JDK: 21+ (Virtual Threads); build: Gradle; docs: `apps/docs/` (Astro + Starlight + `@almasix/starlight-theme`)
- Global CLI: `kona` (`modules/cli`); app-local: `./grind` (`modules/grind`)
- ORM: `kona-extract` (`modules/extract`)
- Samples: `samples/hello-kotlin`, `samples/hello-java`
- CI: `.github/workflows/ci.yml` — tests + Kover 100% on in-scope modules; docs Pages: `.github/workflows/pages.yml`

## How to resume the continuous session

```text
Read AGENTS.md, MILESTONES.md, and docs/governance/continuous-autopilot.md.
Active milestone is whatever MILESTONES.md says (expect M3 after M2).
Run continuous autopilot: exhaust parity + 100% coverage + rewrite docs as needed →
commit → PR → automerge → next milestone until M5+ checklists are done.
Use Auto for all models. Never edit GitHub rulesets.
```

## Local verify before a milestone PR

```bash
export JAVA_HOME=…   # JDK 21+
./gradlew test koverVerify
pnpm --filter @kona/docs build   # or: cd apps/docs && pnpm build
```

## Explicit wiring note (M2)

Migrations run only when the app registers them in a `MigrationRegistry` (no classpath auto-discovery). That matches Kona’s current explicit-provider style; higher-level app scaffolding may come later — document honestly, do not fake auto-discovery.

## Refuse

Skip coverage, skip parity, partial slices, jumping milestones, or changing GitHub rulesets → refuse and continue the current milestone (or wait for a human on rulesets).
