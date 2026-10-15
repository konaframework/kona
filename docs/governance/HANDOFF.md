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
| Last merged milestone | **M16 — Durable queues** on `main` (PR #31) |
| Active | **M17 — Scaffold provider wiring + Redis session + signed cookies** — ready for PR (`milestone/M17-scaffold-session-cookies`) |
| App layout | Strict JVM hybrid under `src/main` (M14). Extract morphs/through/casts/pagination (M15). Durable queues (M16). |
| Next after M17 | Open explicitly after merge (notifications durability / Crema Vite / etc.) |
| Full parity map | [MILESTONES.md](../../MILESTONES.md) |
| Docs brand | Primary `#FF9A00`, accent `#286291` |
| Models | **Auto only** unless the human names a model for that turn |
| Autopilot | Continue deepen milestones after ledger M13; do not pause between merges |
| Merge habit | Enable automerge; squash-merge when CI green if automerge stalls |
| Branch protection | Never push to `main`; never mutate GitHub rulesets (Law 9). |

### M17 focus

Scaffold kits depend on + register Extract/Redis/Session/Auth/Queue/Schedule/Mail; `APP_KEY` + `session.json`; cookie HMAC when encryption off; Redis session via providers; StartSession + CSRF except `/api/*`.

### Recently merged

| Work | PR |
|------|-----|
| M16 Durable queues | #31 |
| docs: mark M16 done | #32 |
| M15 Extract morphs / through / casts / pagination | #30 |
| M14 complete app tree under `src/main` | #29 |

## Known thin surfaces (after M16)

| Area | Thinness |
|------|----------|
| Scaffold / session | In progress M17 |
| Notifications / broadcast / search | In-memory / log-null / memory search |
| Crema | Vite manifest/hot, richer `<x-*>` |

## Sister project — Kona Idea

| | |
|--|--|
| Repo | https://github.com/konaframework/kona-idea |
| Local clone | `/home/smaosa/Projects/kona-idea` |
| Latest release | v0.1.0 |

## Commands

```bash
./gradlew test koverVerify
./gradlew :kona-cli:run --args='new demo --lang=kotlin'
```

## Do not

- Skip coverage or parity; jump milestones; embed Quarkus/Micronaut/Spring.
- Mutate GitHub rulesets.
- Pin named Cursor models unless the human asks.
