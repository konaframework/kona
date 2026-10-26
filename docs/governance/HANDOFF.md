# Agent handoff — Kona

Paste this file (or its path) into a new chat so another agent can continue without the prior transcript.

## Purpose

**Kona** is a from-scratch JVM web framework (`org.konaframework`): Kotlin-first internals, Java first-class public API, Virtual Threads, Netty HTTP, Cadence ORM, Crema templates, Grind Console, drip HTTP client, steam scheduler. Org: [konaframework](https://github.com/konaframework). Public docs: https://docs.konaframework.org/

## Read first (in order)

1. [AGENTS.md](../../AGENTS.md) — milestone laws, architecture locks, Auto model default.
2. [MILESTONES.md](../../MILESTONES.md) — **active** milestone and Almasix parity ledger.
3. [continuous-autopilot.md](continuous-autopilot.md) — uninterrupted drill (implement → docs → PR + automerge; non-colliding milestones may run in parallel).
4. [docs-site.md](docs-site.md) + [`.cursor/rules/kona-docs.mdc`](../../.cursor/rules/kona-docs.mdc) — public docs voice (feature-guide depth; Getting Started stays simple).
5. [docs/architecture/PLAN.md](../architecture/PLAN.md) and [components.md](../architecture/components.md) — product map.
6. [github-rulesets.md](github-rulesets.md) — **never** mutate rulesets (Law 9).
7. [publishing.md](publishing.md) — Maven Local today; Maven Central prerequisites and blockers for `org.konaframework`.
8. Sister IDE repo: [`konaframework/kona-idea` → `HANDOFF.md`](https://github.com/konaframework/kona-idea/blob/main/HANDOFF.md).

## In-repo Wire / Inertia (remonorepo)

| Product | Module | Docs | Public URL |
|---------|--------|------|------------|
| Wire (M26, train **0.3.0**; Brew aliases) | `modules/wire` → `:kona-wire` | `apps/docs` (Frontend → Wire) | https://docs.konaframework.org/docs/wire/ |
| Inertia | `modules/inertia` → `:kona-inertia` | `apps/docs` (Frontend → Inertia) | https://docs.konaframework.org/docs/inertia/ |
| Firefly support (M28, train **0.4.x**) | `modules/firefly-support` → `:kona-firefly-support` | `apps/docs` (Firefly) | https://docs.konaframework.org/docs/firefly/ |
| Firefly schemas (M29, train **0.4.x**) | `modules/firefly-schemas` → `:kona-firefly-schemas` | `apps/docs` (Firefly → Schemas) | https://docs.konaframework.org/docs/firefly/schemas/ |
| Firefly actions (M30, train **0.4.x**) | `modules/firefly-actions` → `:kona-firefly-actions` | `apps/docs` (Firefly → Actions) | https://docs.konaframework.org/docs/firefly/actions/ |
| Firefly infolists (M32, train **0.4.x**) | `modules/firefly-infolists` → `:kona-firefly-infolists` | `apps/docs` (Firefly → Infolists) | https://docs.konaframework.org/docs/firefly/infolists/ |
| Firefly tables (M33, train **0.4.x**) | `modules/firefly-tables` → `:kona-firefly-tables` | `apps/docs` (Firefly → Tables) | https://docs.konaframework.org/docs/firefly/tables/ |
| Firefly forms (M31, train **0.4.x**) | `modules/firefly-forms` → `:kona-firefly-forms` | `apps/docs` (Firefly → Forms) | https://docs.konaframework.org/docs/firefly/forms/ |
| Firefly notifications (M34, train **0.4.x**) | `modules/firefly-notifications` → `:kona-firefly-notifications` | `apps/docs` (Firefly → Notifications) | https://docs.konaframework.org/docs/firefly/notifications/ |
| Firefly widgets (M34, train **0.4.x**) | `modules/firefly-widgets` → `:kona-firefly-widgets` | `apps/docs` (Firefly → Widgets) | https://docs.konaframework.org/docs/firefly/widgets/ |
| Firefly panel (M35, train **0.4.x**) | `modules/firefly` → `:kona-firefly` | `apps/docs` (Firefly → Panel) | https://docs.konaframework.org/docs/firefly/panel/ |
| Wire JS | `js/wire/` | — | bundled into `kona-wire` resources |

Stub only: `apps/wire-docs/README.md`, `apps/inertia-docs/README.md`. **Archived** sister repos: `konaframework/kona-brew`, `konaframework/kona-inertia`.

Other product repo: sister IDE [`konaframework/kona-idea`](https://github.com/konaframework/kona-idea). Do **not** put handoff in `~/hello-kona`.

## Where we are (2026-10-26)

| Item | Value |
|------|--------|
| Default branch | `main` — green; never push directly |
| Repo version | `kona.version` **0.3.0**. M26 (Kona Wire) has shipped on that train. Do not tag **0.4.x** unless a human asks. |
| `origin/main` | `38e5a847` (PR #224, M34 notifications and widgets). #223 is the grind Kover fix |
| On `main` | M28–M34, including forms and notifications/widgets (`38e5a847`, #224). M35 panel is this branch and lands when the PR merges |
| Active milestone | **M35 — Firefly panel, auth, navigation, and pages**, on `milestone/M35-firefly-panel`. Next after merge is **M36** (resources), then M37, M38. Release train **0.4.x** |
| Queued | **M36–M38** not started. M36 is resources. **M27 — Cadence Document / MongoDB** stays Kona **1.x** and human-gated. Do not implement the Mongo addon. Do not run M35–M38 in parallel |
| Ledgers | Cadence / Security / HTTP / Queue-Schedule / Support deepen batches **closed** (#87–#91 + governance #92) |
| App layout | Default `kona new` is **web** (Crema welcome); `--kit=api` / `--kit=inertia` / Wire starter via package |
| Docs brand | Light caramel `#DF8723` / accent `#74453F`; dark latte `#C9A57B`; cream headings `#E8D4B8` |
| Docs versions | `starlight-versions`: **`{major}.x (Latest)`** (default current from `kona.version`), **Dev** (`main` tip archive) |
| Docs i18n | Root English; locales `es` / `fr` / `zh-cn` (Introduction + Installation translated; else EN fallback) |
| LSP | `kona-lsp` completes Crema `@`, Wire `wire:*`, and echo helpers inside `{{ }}` (IDEA-aligned `CremaEchoCatalog`) |
| Models | **Auto only** unless the human names a model for that turn |
| Autopilot | Non-colliding milestones may proceed in parallel. After a merge, continue remaining milestones whose dependencies are on `main`, including ones already in progress. Do not pause for permission |
| Firefly integration | Pages, widgets, and resources compose schemas, forms, infolists, actions, and tables. Standalone render stays available for tests and embedding. M34 widgets place on a `Page` or `Dashboard` and are discovered from a panel `widgets/` directory. M35 and M36 stay queued |
| Merge habit | Enable automerge; squash-merge when CI green if automerge stalls |
| Branch protection | Never push to `main`; never mutate GitHub rulesets (Law 9) |

### Firefly integration contract

Schemas, forms, infolists, actions, and tables can render on their own for tests and embedding. Pages, widgets, and resources compose those pieces. A later milestone cannot ship by only rendering a form, infolist, table, or widget in isolation. M34 notifications and widgets are on `main`. M35 panel, auth, navigation, and pages are done on this branch. M36 is resources. Do not start M36 until M35 is on `main`.

**Pages.** A page is a routable panel screen. It has a title, header and footer actions (the actions module), a content region that mounts a schema, form, infolist, or table, and optional widgets. List, create, edit, view, manage, and custom pages are pages. Standalone component render remains available for tests and embedding, and a Page is the thing a panel route serves. Simple resources use one Manage page whose create/edit/view/delete run in the existing Dialog.

**Widgets.** A widget renders on its own and inside a page or dashboard. Stats, charts (Chart.js default, ApexCharts optional), and table-backed widgets read the same query and schema pieces. A widget is not done if it cannot be placed on a page.

**Resources.** A resource binds one model to its pages, form schema, infolist, table, and actions, including relation managers on a full resource. The model type selects the query (Cadence ModelBuilder versus the document addon later). A resource is not done if the form, infolist, table, and actions exist only as separate samples and are not reachable from the resource's pages.

### Recently merged (this session / adjacent)

| Work | PR |
|------|-----|
| M33 Firefly tables | [#218](https://github.com/konaframework/kona/pull/218) (`1a6045b1`) |
| M30 Firefly actions | [#216](https://github.com/konaframework/kona/pull/216) |
| M32 Firefly infolists | [#217](https://github.com/konaframework/kona/pull/217) |
| M29 Firefly schemas | [#214](https://github.com/konaframework/kona/pull/214) |
| M28 Firefly support | [#213](https://github.com/konaframework/kona/pull/213) |
| Grind `route:list` / `route:cache` / `route:clear` | *(this PR — `feat/grind-route-commands`)* |
| Docs versions + i18n + LSP `wire:*` + GH chip hydrate | *(this PR — `feat/docs-ide-i18n-versions`)* |
| Remonorepo Wire + Inertia into `modules/{brew,inertia}` | [#138](https://github.com/konaframework/kona/pull/138) (landed; supersedes #137 approach) |
| Handoff / publishing Central notes | [#135](https://github.com/konaframework/kona/pull/135) |
| Extract `kona-inertia` + brief docs link-out | [#101](https://github.com/konaframework/kona/pull/101) |
| `./grind install` / `kona grind install` + provider registration | [#100](https://github.com/konaframework/kona/pull/100); top-level `kona install` removed |
| Exhaust Testing section | [#99](https://github.com/konaframework/kona/pull/99) |
| Logging drivers (stack, daily, …) + exhaustive docs | [#98](https://github.com/konaframework/kona/pull/98) |
| Expand Grind Console guides | [#97](https://github.com/konaframework/kona/pull/97) |
| Exhaust Events into dedicated section | [#96](https://github.com/konaframework/kona/pull/96) |
| Crema `view()` / Views helper (share, first) + docs | [#95](https://github.com/konaframework/kona/pull/95) |
| Shrink Wire section to brief link-out | [#94](https://github.com/konaframework/kona/pull/94) |
| Exhaust HTTP + Crema feature guides | [#93](https://github.com/konaframework/kona/pull/93) |
| Close deepen ledger status after #88–#91 | [#92](https://github.com/konaframework/kona/pull/92) |
| Support Collections/Arr/Str/Date deepen | [#91](https://github.com/konaframework/kona/pull/91) |
| Queue full batches | [#90](https://github.com/konaframework/kona/pull/90) |
| HTTP niche validation / stream / controller middleware | [#89](https://github.com/konaframework/kona/pull/89) |
| Security CadenceUserProvider + Argon2 | [#88](https://github.com/konaframework/kona/pull/88) |
| Cadence B6 Eloquent parity | [#87](https://github.com/konaframework/kona/pull/87) |
| Wire cutover to external `kona-wire` | [#79](https://github.com/konaframework/kona/pull/79) |

### Done vs in-flight (honest)

**Done on `main`:** logging drivers, `view()` / Views, Grind Console docs, Events / Testing sections, Wire/Inertia brief link-outs, `./grind install` / `kona grind install`, first `0.1.0` release train tagged on kona (#134). Continuous autopilot + Auto model locks remain in AGENTS / rules.

**Remonorepo done (#138):** Wire + Inertia are in-tree (`modules/wire`, `modules/inertia`, samples, `js/wire`). Docs unified into `apps/docs` (Frontend → Wire / Inertia). **Human Cloudflare follow-up:** redirect `brew.konaframework.org` → `/docs/wire/`, `inertia.konaframework.org` → `/docs/inertia/`; retire Workers `kona-wire-docs` / `kona-inertia-docs` (see [docs-site.md](docs-site.md)).

**Repo version:** `gradle.properties` `kona.version` is **0.3.0** (M26). Confirm Maven Central before describing a version as downloadable. Do not tag Firefly **0.4.x** unless a human asks. Sister IDE: [`kona-idea`](https://github.com/konaframework/kona-idea) latest tag tracked in that repo’s `HANDOFF.md`.

**Recently closed:** [#150](https://github.com/konaframework/kona/issues/150) grind/Cadence/Wire parity — `make:model` `-mcrfspaR` / `--api` / `--requests` companions (#151 + seal PR), `META-INF/kona/migrations` + `MigrationDiscovery` auto-register, schema `longText`/`json`, quiet grind launcher + serve banner (#152), `@CremaTemplate` / `// language=Crema` for inline Wire templates.

**Scaffold indent:** `kona new` and `./grind install inertia` emit 2-space `package.json` / `tsconfig.json` via `PrettyJson` and assemble `vite.config.js` / Wire welcome HTML with `SourceBlocks` so nested `trimIndent()` cannot collapse blocks to column 0. Sister IDE **kona-idea 0.5.20** lists those frontend files in the Kona Project View.

### Intentional deferrals (do not reopen without mandate)

- Horizon / queue metrics dashboard
- Cross-process batch `then`/`catch` callbacks (durable counts only)
- Lazy collections / higher-order proxies
- Full Carbon locale / human-diff surface
- ~~`make:migration` auto-register into `MigrationRegistry`~~ — done (`META-INF/kona/migrations` + `MigrationDiscovery`; see #150)
- Pivot / morph-pivot `make:model` stubs — defer until Cadence pivot model surface is first-class

### Notes

- Do **not** revive Extract names — ORM is **Cadence**.
- **CLI lock:** user-facing terminal output uses Termwind-style `ConsoleIO` (`kona-support`); grind wraps it as `GrindIO`. No raw `println`.
- Wire and Inertia exhaustive guides live under `apps/docs` sidebar **Frontend → Wire / Inertia** (not separate sites).

## How to continue

```bash
# Sync
git fetch origin && git checkout main && git pull

# Full test + 100% coverage gate
./gradlew test koverVerify

# Scaffold
./gradlew :kona-cli:run --args='new demo --lang=kotlin'
# or global CLI after install: kona new demo --lang=kotlin

# Docs (local)
pnpm install --frozen-lockfile && pnpm docs:dev          # apps/docs (includes Wire + Inertia)

# Publish all jars incl. brew/inertia
./gradlew publishToMavenLocal -x test
# Full maintainer guide: docs/governance/publishing.md
```

Key paths: `modules/` (framework), `apps/docs/`, `js/wire/`, `docs/governance/`, `.cursor/rules/kona-*.mdc`.

## Open gaps / next priorities

1. Cloudflare: redirect/retire `brew.konaframework.org` / `inertia.konaframework.org` (see docs-site.md).
2. Confirm no conflicting open PRs.
3. Pick remaining **deepen** slices from the Almasix ledger. Non-colliding milestones may proceed in parallel, each on its own branch. Do not start a milestone that depends on another until that dependency is on `main`. **M26 Wire** shipped on **0.3.0**.
4. **Maven Central — `v0.2.0` shipped** (do not re-tag). **`0.2.1` version bump on `main` — do not tag until release cut.** Optional follow-ups:
   - File Count monthly limit (grace through **2026-11-17**): check Usage Center; request higher limit if needed. Checksum-of-`.asc` reduction needs Gradle 9 + vanniktech ≥0.37 (deferred).
   - Trusted Publishing (OIDC) still **not GA** — token + GPG. Full checklist: [publishing.md](publishing.md).

## Do not

- Skip coverage or parity; run parallel work that collides or depends on a milestone that is not yet on `main`; embed Quarkus/Micronaut/Spring.
- Mutate GitHub rulesets.
- Pin named Cursor models unless the human asks.
- Commit `~/hello-kona` or treat it as a product repo.
