# Agent instructions — Kona

Read this file and the **current milestone** section in [MILESTONES.md](MILESTONES.md) before writing any code.

## Milestone laws (non-negotiable)

1. **One active milestone at a time.** Do not start the next milestone until the current one is merged to `main`.
2. **Develop to exhaustion.** Within the milestone’s declared parity scope, implement completely — no “good enough for now,” no deferred core behaviors inside that scope.
3. **100% code coverage** for all production code introduced or touched by the milestone (line + branch via Kover/JaCoCo). CI must fail if coverage is below 100% on in-scope modules.
4. **Regression suite.** Extend automated tests each milestone. The full suite for all *completed* milestones must stay green. Never merge a change that breaks prior milestones.
5. **Slice parity gate.** Done only when the milestone checklist shows **100% parity** with the corresponding **Laravel and/or Almasix** surface (behavioral/API shape — not PHP/Python source identity). Prefer Almasix where both exist; use Laravel as the named equivalent for gaps.
6. **Commit + automerge, then next.** Milestone PR → required CI green → automerge to `main` → only then open the next milestone branch.
7. **No engine pivots.** From-scratch Kona only. Do **not** put Quarkus, Micronaut, or Spring under the hood.
8. **Kotlin-first, Java first-class.** Every milestone that ships public API includes Java-callable usage and a Java sample/regression path.

## Refuse and redirect

If a human or prompt asks to skip coverage, skip parity, ship a partial slice, or start the next milestone early: **refuse**, explain which law applies, and finish the current milestone.

## Architecture locks

- Language: Kotlin internals; Java apps supported equally (`dev.kona` artifacts).
- Concurrency: Virtual Threads (JDK 21+); not coroutines-first HTTP.
- Compose libraries (Netty, HikariCP, Jackson/kotlinx.serialization, Lettuce, SLF4J). Steal *ideas* from Quarkus/Micronaut (e.g. KSP indexes), never embed those frameworks.
- Product names: `Kona`, `grind`, `steam`, `Extract`, `Crema`, `drip` — see [docs/architecture/components.md](docs/architecture/components.md).

## Before coding

1. Confirm which milestone is active in [MILESTONES.md](MILESTONES.md).
2. Work only on that milestone’s branch (`milestone/M…`).
3. Update the milestone parity checklist as items land.
4. Do not expand scope into a later milestone’s checklist.
