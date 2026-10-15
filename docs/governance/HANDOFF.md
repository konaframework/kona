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
| Last merged milestone | **M14 — Complete app tree under `src/main`** on `main` (PR #29) |
| Active | **M15 — Extract / Model Laravel parity** (`milestone/M15-extract-model-parity`) — parity checklist complete; ship PR → automerge |
| App layout | **Strict JVM hybrid under `src/main`:** `src/main/{kotlin\|java}/` + `src/main/resources/` + project-root `storage/` + `.env` / `.env.example`. Lowercase packages. `PathLayout` / `Paths`. Kits: api/crema/inertia/brew. |
| Next after M15 merges | Further deepen / maintenance milestones (queue, session durability, scaffold provider wiring) — open explicitly |
| Full parity map | [MILESTONES.md](../../MILESTONES.md) |
| Docs brand | Primary `#FF9A00`, accent `#286291` (`apps/docs/src/styles/custom.css`) |
| Docs shape | Progressive guides; rewrite as features land. |
| Models | **Auto only** unless the human names a model for that turn |
| Autopilot | Continuous through Almasix ledger; after M15 merges, continue deepen queue |
| Merge habit | Enable automerge; **once required CI is green, squash-merge immediately** if automerge stalls. PRs that only touch `docs/governance` may skip **Build site** — touch `apps/docs/` so that required check runs. |
| Branch protection | Never push to `main`; never mutate GitHub rulesets (Law 9). |

### M15 deliverables (this branch)

- Schema `morphs` / `nullableMorphs` / `uuidMorphs`
- `MorphTo` / `MorphMany` / `MorphOne` + `Relation.morphMap`
- `HasManyThrough` / `HasOneThrough`
- Custom `CastsAttributes`; `casts` accepts `CastType` or custom casts
- `simplePaginate` + `cursorPaginate` (`SimplePage` / `CursorPage`)
- `withExists` / `withSum` + `loadExists` / `loadSum`; `wasChanged` / `getChanges`
- Extract docs + Kotlin/Java regressions; samples remain `src/main` tree from M14

### Recently merged

| Work | PR / location |
|------|----------------|
| M14 complete app tree under `src/main` | PR #29 |
| Post-M13 kit classpath + serve regressions | PR #23 |
| Crema Blade/Prism app-facing directives + docs | PR #24 |
| Extract Eloquent-shaped deepen + docs | PR #25 |
| Docs → native IntelliJ plugin | PR #26 |
| Native IntelliJ plugin (separate repo) | [`konaframework/kona-idea`](https://github.com/konaframework/kona-idea) `v0.1.0` |

## Known thin surfaces (ledger “done”, deepen later)

| Area | Thinness |
|------|----------|
| Scaffold / `kona new` | Some kits need manual provider/deps for queue/schedule/mail/session/auth; grind `queue:*` / `schedule:*` fail if unbound |
| Queue | Job payload not fully cross-process (`JobRegistry` in-process); `release` delay ignored; failed jobs in-memory; `queue:retry` forgets without re-dispatch |
| Notifications / broadcast / search | “Database” notifications in-memory; broadcast = log/null; search = collection/memory only |
| Session / auth | Redis session store may not be injected by `SessionServiceProvider`; cookie session unsigned; users/tokens/password resets often in-memory |
| Cache / Redis / support | No cache locks/tags/increment; Redis client surface minimal; no lang-file loader |
| Events / mail | Sync bus only; SMTP minimal (no attachments / queued mailables) |
| Testing / drip | HTTP client get+postJson; fakes incomplete; drip no patch/multipart |
| Crema | Vite manifest/hot, richer `<x-*>` still thin |

**Preferred deepen order** after M15: durable queue, scaffold provider wiring, Redis session + signed cookies.

## Sister project — Kona Idea

| | |
|--|--|
| Repo | https://github.com/konaframework/kona-idea |
| Local clone (this machine) | `/home/smaosa/Projects/kona-idea` |
| Plugin id | `dev.kona.idea` |
| Latest release | [v0.1.0](https://github.com/konaframework/kona-idea/releases/tag/v0.1.0) (`kona-idea-0.1.0.zip`) |
| Handoff | [HANDOFF.md](https://github.com/konaframework/kona-idea/blob/main/HANDOFF.md) in that repo |
| Follow-up | Remake plugin assumptions for complete `src/main` tree / path helpers after M14 |

When adding Crema directives in `modules/lsp` / `CremaRuntime`, update `dev.kona.idea.CremaDirectives` in kona-idea and cut a patch release.

## Commands agents use constantly

```bash
./gradlew test koverVerify
./gradlew :kona-cli:run --args='new demo --lang=kotlin'
cd demo && ./grind serve
```

Public docs: `cd apps/docs && npm ci && npm run build` (or repo CI **Build site**).

## Do not

- Skip coverage or parity; jump milestones; embed Quarkus/Micronaut/Spring.
- Mutate GitHub rulesets / ask for `admin:org` to edit rules.
- Pin named Cursor models unless the human asks.
