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
| Last merged milestone | **M10 — Broadcasting and search** (`main`, PR #16) |
| Parity hardening | **M3–M10 parity round2** on `fix/m3-m10-parity-round2` (cookie encryption, lang files, collections, file failed jobs, testing/drip deepen, Crema docs split, Url helpers, make:middleware/request, websocket broadcast, package auto-discovery, notification file persistence). Do **not** start M11 until this merges. |
| Next / active after merge | **M11 — Reactive UI** (`milestone/M11-reactive-ui`) |
| Full parity map | [MILESTONES.md](../../MILESTONES.md) Almasix coverage ledger → through **M13** |
| Docs brand | Primary `#FF9A00`, accent `#286291` (`apps/docs/src/styles/custom.css`) |
| Docs shape | Still low-level (providers, explicit wiring). Trajectory: Laravel-style progressive guides (simple first, dig deeper later). Rewrite as features land — do not freeze the current structure. |
| Models | **Auto only** unless the human names a model for that turn |
| Autopilot | Continuous M3→M13 (full Almasix menu); no pause between merges |

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
If fix/m3-m10-parity-round2 (or a successor parity branch) is still open, finish that
first — M11 is next only after merge. Otherwise active milestone is M11 — reactive UI.
Run continuous autopilot: exhaust parity + 100% coverage + rewrite docs as needed →
commit → PR → automerge → next milestone until the Almasix coverage ledger is fully done (through M13).
Use Auto for all models. Never edit GitHub rulesets.
```

## Local verify before a milestone PR

```bash
export JAVA_HOME=…   # JDK 21+
./gradlew test koverVerify
pnpm --filter @kona/docs build   # or: cd apps/docs && pnpm build
```

## Fresh `kona new` apps

Artifacts are `0.1.0-SNAPSHOT` from `mavenLocal()` unless `KONA_HOME` / `-Pkona.home` composite-builds a checkout.
Publish with `./gradlew publishToMavenLocal` from this repo before expecting a scaffolded app to resolve deps.
The skeleton only registers `HttpServiceProvider`; register Extract/Session/Auth/Queue/… providers when adopting those grind commands.

## Explicit wiring note (M2)

Migrations run only when the app registers them in a `MigrationRegistry` (no classpath auto-discovery). That matches Kona’s current explicit-provider style; higher-level app scaffolding may come later — document honestly, do not fake auto-discovery.

## Refuse

Skip coverage, skip parity, partial slices, jumping milestones, or changing GitHub rulesets → refuse and continue the current milestone (or wait for a human on rulesets).
