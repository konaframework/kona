# Continuous milestone autopilot (locked)

Applies to the **next** long agent session and every successor until **M13** (and any later milestones listed in [MILESTONES.md](../../MILESTONES.md)) are on `main`. The Almasix parity coverage ledger in that file is the completion gate — do not stop at the old M5.

## Drill (do not pause)

For each remaining coding milestone (M3 → M13), in order:

1. Confirm the active milestone in [MILESTONES.md](../../MILESTONES.md); open/create `milestone/M…` from current `main`.
2. Implement the slice **to exhaustion** — full Laravel / Almasix behavioral parity within the checklist (Law 2 + Law 5).
3. Hit **100%** line + branch coverage on in-scope production modules; keep the full regression suite green (Laws 3–4).
4. Write or **rewrite** public docs under `apps/docs/` for that slice so the site stays developer-friendly (progressive path toward simple-first guides — see [docs-site.md](docs-site.md)).
5. Commit on the milestone branch → open PR → wait for required CI → **enable automerge** (squash) → wait until merged to `main`.
6. Update [MILESTONES.md](../../MILESTONES.md) (milestone **done**, next **active**) if not already done in the PR, then **immediately** start the next milestone. Do **not** stop to ask whether to continue.

Repeat until every row in the Almasix parity coverage ledger is **done** (through M13).

## Non-negotiable pauses (only)

Stop and surface to a human **only** when:

- A GitHub **ruleset** blocks progress (Law 9 — never edit rulesets).
- Security / credentials / destructive git action needs explicit human approval.
- The environment is broken in a way you cannot fix (missing secrets, JDK, network) after reasonable attempts.

Do **not** pause between milestones for celebration, planning permission, or “should we start M4?”. The answer is always yes if the previous milestone is on `main`.

**Exception — Kona 1.x:** milestones listed under **Kona 1.x (queued — human-gated)** in [MILESTONES.md](../../MILESTONES.md) (today **M27 Cadence Document / MongoDB**) are **not** the next autopilot step. Do not open those branches until a human sets **Active milestone** to that item.

## Models

Use **Auto** (Cursor Auto / `inherit`) for the parent agent and all subagents. Do **not** pin Opus, Sonnet, GPT, Grok, or other named models unless a human explicitly overrides for that turn.

## Parallelism

Within one milestone, parallel subagents are fine for disjoint packages (e.g. relations vs migrations) as long as they stay on the same branch, leave commits to the parent, and the parent integrates before the PR. Never open the next milestone branch early.
