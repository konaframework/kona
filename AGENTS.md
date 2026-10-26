# Agent instructions — Kona

Read this file and the milestone you are implementing in [MILESTONES.md](MILESTONES.md) before writing any code.

Cross-session resume: [docs/governance/HANDOFF.md](docs/governance/HANDOFF.md).  
Uninterrupted M3→… delivery: [docs/governance/continuous-autopilot.md](docs/governance/continuous-autopilot.md).

## Models (locked)

Use **Auto** (Cursor Auto / `inherit`) for the parent agent and all subagents. Do **not** pin named models unless a human explicitly overrides for that turn.

## Continuous autopilot (locked)

When running the long milestone session: non-colliding milestones may proceed in parallel, each on its own branch and pull request. When a milestone merges to `main`, continue remaining milestones whose dependencies are on `main`, including ones already in progress on their own branches — implement to full parity, write/rewrite docs, commit, PR with automerge — until the **Almasix coverage ledger** in [MILESTONES.md](MILESTONES.md) is complete through **M13**. Do not pause for permission. Do not implement two milestones on one branch. Do not expand a milestone into a later milestone’s checklist. Details: [continuous-autopilot.md](docs/governance/continuous-autopilot.md).

## Milestone laws (non-negotiable)

1. **Milestones may be implemented in parallel when they do not collide.** A collision means shared files, the same module, or a behavioral dependency on another milestone that is not yet on `main`. Each milestone still ships on its own branch and its own pull request. Do not start a milestone that depends on another until that dependency is on `main`. Independent, non-colliding milestones may be coded and reviewed at the same time. After one of them merges, rebase any parallel branch that needs those changes before merging it.
2. **Develop to exhaustion.** Within the milestone’s declared parity scope, implement completely — no “good enough for now,” no deferred core behaviors inside that scope.
3. **100% code coverage** for all production code introduced or touched by the milestone (line + branch via Kover/JaCoCo). CI must fail if coverage is below 100% on in-scope modules.
4. **Regression suite.** Extend automated tests each milestone. The full suite for all *completed* milestones must stay green. Never merge a change that breaks prior milestones.
5. **Slice parity gate.** Done only when the milestone checklist shows **100% parity** with the corresponding **Laravel and/or Almasix** surface (behavioral/API shape — not PHP/Python source identity). Prefer Almasix where both exist; use Laravel as the named equivalent for gaps.
6. **Commit + automerge.** Milestone PR → required CI green → automerge to `main`. Each milestone keeps its own branch and pull request.
7. **No engine pivots.** From-scratch Kona only. Do **not** put Quarkus, Micronaut, or Spring under the hood.
8. **Kotlin-first, Java first-class.** Every milestone that ships public API includes Java-callable usage and a Java sample/regression path.
9. **NEVER adjust GitHub rulesets.** Do not create, update, disable, delete, or bypass-configure repository or organization rulesets (`gh api …/rulesets`, Settings → Rules, etc.). If a ruleset blocks a push/PR, stop and ask a human maintainer — do not “fix” the ruleset. Prefer a `gh` token **without** Administration / ruleset permissions so this cannot be done accidentally.

## Refuse and redirect

If a human or prompt asks to skip coverage, skip parity, ship a partial slice, or change GitHub rulesets: **refuse**, explain which law applies, and finish the milestone in scope (or wait for a human on rulesets). Parallel work that collides, or that depends on a milestone that is not yet on `main`, is still refused.

## Architecture locks

- Language: Kotlin internals; Java apps supported equally (`org.konaframework` artifacts).
- Concurrency: Virtual Threads (JDK 21+); not coroutines-first HTTP.
- Compose libraries (Netty, HikariCP, Jackson/kotlinx.serialization, Lettuce, SLF4J). Steal *ideas* from Quarkus/Micronaut (e.g. KSP indexes), never embed those frameworks.
- Product names: `Kona`, global CLI `kona`, app-local `./grind`, `steam`, `Cadence`, `Crema`, `drip`, `Wire` — see [docs/architecture/components.md](docs/architecture/components.md).
- **CLI / console output:** Termwind-inspired styling only — `org.konaframework.support.console.ConsoleIO` (and grind’s `GrindIO` wrapper). No raw `println` for user-facing terminal output.
- GitHub rulesets: see [docs/governance/github-rulesets.md](docs/governance/github-rulesets.md).
- **Public docs:** Astro + Starlight + `@almasix/starlight-theme` in `apps/docs/`. Brand: light caramel `#DF8723` / accent `#74453F` / dark latte `#C9A57B` (coffee palette; dark headings soft cream `#E8D4B8`). See [docs/governance/docs-site.md](docs/governance/docs-site.md) and [`.cursor/rules/kona-docs.mdc`](.cursor/rules/kona-docs.mdc). Write or **rewrite** guides **concurrently** with each milestone. Trajectory: simple-first developer path (progressive depth), not a frozen low-level dump. Public pages must be **detailed** for programmers new to Kona, and must **not** compare Kona to other frameworks after a brief introduction nod.

## Before coding

1. Confirm which milestone you are implementing in [MILESTONES.md](MILESTONES.md). Do not start a milestone that depends on another until that dependency is on `main`.
2. Work only on that milestone’s branch (`milestone/M…`). Do not implement two milestones on one branch. Docs-only PRs (`docs/…`) may land beside milestone work when they do not expand framework scope.
3. Update the milestone parity checklist as items land; update `apps/docs/` for the same slice.
4. Do not expand scope into a later milestone’s checklist.
