# Continuous milestone autopilot (locked)

Applies to the **next** long agent session and every successor until **M13** (and any later milestones listed in [MILESTONES.md](../../MILESTONES.md)) are on `main`. The Almasix parity coverage ledger in that file is the completion gate — do not stop at the old M5.

## Drill (do not pause)

Non-colliding milestones may proceed in parallel. For each remaining coding milestone whose dependencies are on `main` (through M13, and any later milestones listed in [MILESTONES.md](../../MILESTONES.md)):

1. Confirm the milestone in [MILESTONES.md](../../MILESTONES.md); open or continue `milestone/M…` from current `main`. One milestone per branch. Do not start a milestone that depends on another until that dependency is on `main`.
2. Implement the slice **to exhaustion** — full Laravel / Almasix behavioral parity within the checklist (Law 2 + Law 5).
3. Hit **100%** line + branch coverage on in-scope production modules; keep the full regression suite green (Laws 3–4).
4. Write or **rewrite** public docs under `apps/docs/` for that slice so the site stays developer-friendly (progressive path toward simple-first guides — see [docs-site.md](docs-site.md)).
5. Commit on the milestone branch → open PR → wait for required CI → **enable automerge** (squash) → wait until merged to `main`.
6. Update [MILESTONES.md](../../MILESTONES.md) (that milestone **done**) if not already done in the PR, then continue remaining milestones whose dependencies are on `main`, including ones already in progress on their own branches. After one merges, rebase any parallel branch that needs those changes before merging it. Do **not** stop to ask whether to continue. Do not implement two milestones on one branch. Do not expand a milestone into a later milestone’s checklist.

Repeat until every row in the Almasix parity coverage ledger is **done** (through M13).

## Non-negotiable pauses (only)

Stop and surface to a human **only** when:

- A GitHub **ruleset** blocks progress (Law 9 — never edit rulesets).
- Security / credentials / destructive git action needs explicit human approval.
- The environment is broken in a way you cannot fix (missing secrets, JDK, network) after reasonable attempts.

Do **not** pause for permission. Continue any remaining milestone whose dependencies are on `main`, including work already in progress on its own branch.

**Exception — Kona 1.x:** milestones listed under **Kona 1.x (queued — human-gated)** in [MILESTONES.md](../../MILESTONES.md) (today **M27 Cadence Document / MongoDB**) are **not** the next autopilot step. Do not open those branches until a human sets **Active milestone** to that item.

## Models

Use **Auto** (Cursor Auto / `inherit`) for the parent agent and all subagents. Do **not** pin Opus, Sonnet, GPT, Grok, or other named models unless a human explicitly overrides for that turn.

## Parallelism

Non-colliding milestones may be coded and reviewed at the same time, each on its own branch and pull request. A collision means shared files, the same module, or a behavioral dependency on another milestone that is not yet on `main`. Do not start a milestone that depends on another until that dependency is on `main`. Do not implement two milestones on one branch. Do not expand a milestone into a later milestone’s checklist. After one milestone merges, rebase any parallel branch that needs those changes before merging it.

Within one milestone, parallel subagents are fine for disjoint packages (e.g. relations vs migrations) as long as they stay on the same branch, leave commits to the parent, and the parent integrates before the PR.
