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
| Cadence B6 | **done** (#87) |
| Security deepen | **done** (#88) |
| HTTP niche validation | **done** (#89) |
| Queue full batches | **done** (#90) |
| Support Collections/Arr/Str/Date | **done** (#91) |
| Active | Scan remaining ledgers for intentional deferrals only; next deepen TBD from Almasix ledger gaps |
| Ledgers | Cadence / Security / HTTP / Queue-Schedule / Support — deepen batches closed |
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
| Support Collections/Arr/Str/Date deepen | #91 |
| Queue full batches | #90 |
| HTTP niche validation / stream / controller middleware | #89 |
| Security CadenceUserProvider + Argon2 | #88 |
| Cadence B6 Eloquent parity | #87 |
| Docs Crema guides + named slots | #86 |
| Extract → Cadence rename | #85 |

### Intentional deferrals (do not reopen without mandate)

- Horizon / queue metrics dashboard
- Cross-process batch `then`/`catch` callbacks (durable counts only)
- Lazy collections / higher-order proxies
- Full Carbon locale / human-diff surface
- Brew (lives in kona-brew; ignore here)
- Inertia (lives in kona-inertia; ignore here)

### Notes

- Do **not** revive Extract names.
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
