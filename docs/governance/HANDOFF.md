# Agent handoff — Kona

Paste this file (or its path) into a new chat so another agent can continue without the prior transcript.

## Read first (in order)

1. [AGENTS.md](../../AGENTS.md) — milestone laws, architecture locks, Auto model default.
2. [MILESTONES.md](../../MILESTONES.md) — **active** milestone and parity checklists.
3. [continuous-autopilot.md](continuous-autopilot.md) — uninterrupted drill (implement → docs → PR + automerge → next).
4. [docs-site.md](docs-site.md) + [`.cursor/rules/kona-docs.mdc`](../../.cursor/rules/kona-docs.mdc) — public docs voice.
5. [docs/architecture/PLAN.md](../architecture/PLAN.md) and [components.md](../architecture/components.md) — product map.
6. [github-rulesets.md](github-rulesets.md) — **never** mutate rulesets.
7. Sister IDE repo handoff: [`konaframework/kona-idea` → `HANDOFF.md`](https://github.com/konaframework/kona-idea/blob/main/HANDOFF.md).

## Where we are (update when handing off)

| Item | Value |
|------|--------|
| Last merged milestone | **M24 — Executable config** on `main` (PR #53) |
| Active | Extract/Database deepen (**B4** morphToMany after B3 merges); see [extract-eloquent-parity.md](extract-eloquent-parity.md) |
| Extract ledger | [extract-eloquent-parity.md](extract-eloquent-parity.md) — B1 #65, B2 #70 done; B3 global scopes shipping; H2 default kept |
| App layout | Default `kona new` is **web** (Crema welcome); `--kit=api` opt-in |
| Full parity map | [MILESTONES.md](../../MILESTONES.md) |
| Docs brand | Primary `#FF9A00`, accent `#286291` |
| Models | **Auto only** unless the human names a model for that turn |
| Autopilot | Continue deepen milestones; do not pause between merges |
| Merge habit | Enable automerge; squash-merge when CI green if automerge stalls |
| Branch protection | Never push to `main`; never mutate GitHub rulesets (Law 9). |

### Recently merged

| Work | PR |
|------|-----|
| Extract B2 whereDate* + ModelBuilder mirrors | #70 |
| Extract B1 builder + DB docs | #65 |
| M24 Executable config | #53 |
| M23 Binary downloads & signed storage | #42 |
| M22 Multipart uploads | #40 |
| M21 Web form round-trip | #39 |
| M20 Default web scaffold | #38 |

### Notes

- Strong M25 candidate: session auth middleware + Extract user provider + remember-cookie (web login).
- **CLI lock:** all terminal output uses Termwind-style `ConsoleIO` (`kona-support`); grind wraps it as `GrindIO`.

## Commands

```bash
./gradlew test koverVerify
./gradlew :kona-cli:run --args='new demo --lang=kotlin'
```

## Do not

- Skip coverage or parity; jump milestones; embed Quarkus/Micronaut/Spring.
- Mutate GitHub rulesets.
- Pin named Cursor models unless the human asks.
