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
| Last merged milestone | **M17 — Scaffold + session/cookies** on `main` (PR #34) |
| Active | **M18 — Notifications durability** (`milestone/M18-notifications-durability`) |
| App layout | Strict JVM hybrid under `src/main`. Extract, durable queues, scaffold providers (M14–M17). |
| Next after M18 | Crema Vite manifest/hot (or next thin surface) |
| Full parity map | [MILESTONES.md](../../MILESTONES.md) |
| Docs brand | Primary `#FF9A00`, accent `#286291` |
| Models | **Auto only** unless the human names a model for that turn |
| Autopilot | Continue deepen milestones; do not pause between merges |
| Merge habit | Enable automerge; squash-merge when CI green if automerge stalls |
| Branch protection | Never push to `main`; never mutate GitHub rulesets (Law 9). |

### M18 focus

Extract-backed notification store, unread / mark-as-read, ShouldQueue via Bus, keep JSON/memory drivers.

### Recently merged

| Work | PR |
|------|-----|
| M17 Scaffold + Redis session + signed cookies | #34 |
| docs: brand marks + Kotlin/Java tabs | #33 |
| M16 Durable queues | #31 |

## Known thin surfaces (after M17)

| Area | Thinness |
|------|----------|
| Notifications | In progress M18 |
| Crema | Vite manifest/hot, richer `<x-*>` |
| Broadcast / search | In-process / memory engines |

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
