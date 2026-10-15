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
| Last merged milestone | **M15 — Extract / Model Laravel parity** on `main` (PR #30) |
| Active | **M16 — Durable queues** (`milestone/M16-durable-queue`) — checklist complete; ship PR → automerge |
| App layout | Strict JVM hybrid under `src/main` (M14). Extract morphs/through/casts/pagination (M15). |
| Next after M16 | Scaffold provider wiring, Redis session + signed cookies |
| Full parity map | [MILESTONES.md](../../MILESTONES.md) |
| Docs brand | Primary `#FF9A00`, accent `#286291` |
| Models | **Auto only** unless the human names a model for that turn |
| Autopilot | Continue deepen milestones after ledger M13; do not pause between merges |
| Merge habit | Enable automerge; squash-merge when CI green if automerge stalls |
| Branch protection | Never push to `main`; never mutate GitHub rulesets (Law 9). |

### M16 focus

Cross-process job restore (payload body JSON), release delays, durable failed jobs, reliable `queue:retry`.

### Recently merged

| Work | PR |
|------|-----|
| M15 Extract morphs / through / casts / pagination | #30 |
| M14 complete app tree under `src/main` | #29 |

## Known thin surfaces (after M15)

| Area | Thinness |
|------|----------|
| Queue | M16: jobBody JSON + database failed_jobs; remaining: scaffold auto-wiring of QueueServiceProvider |
| Scaffold / `kona new` | Kits may need manual provider/deps for queue/schedule/mail/session/auth |
| Session / auth | Redis session injection; unsigned cookies |
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
