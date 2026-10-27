# Firefly views — Wire + Crema (locked)

Agents and maintainers: this rule overrides any earlier habit of shipping Firefly chrome or components as Kotlin string HTML “builders.” Public docs do not compare Firefly to Filament; this file may name Filament and Shamar for parity mapping only.

## Locked rule

**Firefly chrome and UI components are Wire components with Crema views. Kotlin (or Java) describes structure, config, and behavior. Crema owns markup.**

Exact text to paste when citing the rule:

> Every Firefly component or UI needs to derive from Wire components, and Wire components come as a class file and a Crema file. No escaping views unless Kotlin rendering is proven faster. Filament uses Livewire views for layout. Custom-made chrome in Kotlin instead of Crema layouts is the wrong default — same for custom forms and tables: define the class, specify the backing view, keep markup in Crema (one-liner include / `@component` in the view).

Refinements that stay in force:

1. **Class + Crema is the default.** Match Wire’s own default (`./grind make:wire` → class + `views/wire/<name>.crema.html`). Inline `template()` is for tiny SFCs, not panel chrome or tables.
2. **Kotlin describes structure/config; Crema owns markup.** Fluent APIs (`Panel`, `Table`, `Field`, schema trees) stay in Kotlin/Java. Document shells, sidebar/topbar/body/footer, table chrome, field wrappers, and page frames live in `*.crema.html`.
3. **Do not reject Crema on “Kotlin is faster”** without a measured benchmark on a real panel path. String builders vs Crema templates are not the main UX bottleneck — structure and parity of markup + CSS are. Maintainability and Shamar/Filament-shaped authoring favor Wire + Crema.
4. **Escape hatch.** Raw Kotlin HTML or `WireView.raw` only when a profiled hot path proves Crema too slow, or for a temporary bridge during migration with a tracked delete date. Prefer moving markup into Crema in the same PR that adds the surface when feasible.

## Why (evidence, not slogans)

### Wire already is class + Crema

- `org.konaframework.wire.Component` — `render()` returns a `WireView`, raw HTML, or `null`; `template()` is optional inline Crema; convention view is `views/wire/<name>.crema.html` (`WireConfig.viewPrefix`). See `modules/wire/.../Component.kt`, `WireView.kt`, `WireManager.viewFor` / `renderView`.
- `WireView.named` / `layout` / `slot` mirror Livewire’s view + layout + slots.
- Grind: `make:wire` defaults to a separate Crema file (`--class`); `--inline` is the opt-in SFC.

### Filament / Livewire

Filament pages and chrome sit on Livewire components whose **layout and Blade views** own the frame. The PHP class configures; the view composes. Firefly’s Filament-like *config* APIs were correct; putting the *chrome* in Kotlin string templates was the wrong half of the analogy.

### Shamar / Almasix

Shamar’s admin shell is Edge + Wire/Alpine: e.g. `packages/adonis/resources/views/shamar/partials/shell-open.edge`, `nav`, `list-toolbar`, `fields/*`. That maps cleanly to Crema + Wire. Matching Shamar visually while authoring markup only in Kotlin fights the sister product’s shape.

### What Firefly already has (use it)

| Surface | Crema / view path today |
|---------|-------------------------|
| Schema components | `Component.view(name)` + `Schema.crema(engine)` → `renderView` |
| Custom fields / columns / entries / actions / widgets / layouts | `./grind make:firefly-{field,column,entry,action,widget,layout}` writes **class + `views/firefly/...crema.html`** |
| Theme / app partial overrides | `Panel.overridePartial` / theme `partial` (HTML strings today; should become Crema partials) |

### What is still pure Kotlin HTML (migrate)

| Surface | Primary files |
|---------|----------------|
| Auth document shell | `modules/firefly-support/.../AuthLayout.kt` (still Kotlin HTML) |
| Sidebar / topbar / user menu / auth forms | `modules/firefly/.../Panel.kt` (string partials) |
| Tables (toolbar, scroll, pagination, cards) | `modules/firefly-tables/.../Table.kt` and neighbors |
| Built-in form field chrome | `modules/firefly-forms` default `render` paths (custom fields already prefer `.view(...)`) |
| Dialogs / some support widgets | `Dialog.kt` and related support HTML |

**Phase A landed:** `:kona-firefly-support` depends on Crema. `DefaultLayout` renders `views/firefly/layouts/panel.crema.html` via `FireflyCrema` / `ClasspathViewFinder`. Wire page-host for the shell is deferred; auth remains Phase B.

## Migration sketch (phased)

Do **not** boil the ocean in one PR. Each phase: own branch, 100% coverage on touched production code, public docs updated, no expansion into a later milestone’s checklist.

### Phase A — Panel chrome as Wire + Crema layout

**Goal:** `DefaultLayout` (and the document shell) render from Crema; Kotlin only supplies partial data and hook HTML.

| Change | Notes |
|--------|--------|
| Add Crema dependency to `:kona-firefly-support` (or extract `kona-firefly-chrome`) | **Done** — `api(project(":kona-crema"))`; package views under `views/firefly/layouts/` |
| Ship `panel.crema.html` / `auth.crema.html` matching today’s DOM + hook markers | **`panel.crema.html` done**; `auth.crema.html` → Phase B. Keep `<!-- ff-hook:… -->` contract |
| `PanelLayout` impl loads Crema (engine from app or bundled finder) | **Done** — `DefaultLayout` + `FireflyCrema` / `ClasspathViewFinder` |
| Wire: optional `FireflyChrome` / page host component | **Deferred** — Phase A is Crema invoked from `DefaultLayout` (documented first vertical slice) |

**Risks:** Kover on new branches; CSS class stability for desk/Shamar polish PRs; tests that assert exact HTML strings need Crema-rendered fixtures.

**Status:** Phase A wiring is on `feat/firefly-wire-crema-chrome`. Next: Phase B auth shell.

### Phase B — Auth shell

Move auth document + login/register/reset form markup from `AuthLayout` / `Panel.kt` auth HTML into `views/firefly/auth/*.crema.html`. Keep CSRF and guard wiring in Kotlin.

### Phase C — Table / toolbar partials

Extract toolbar, scroll table, empty state, pagination, and card layout into `views/firefly/tables/*.crema.html`. `Table.render()` assembles data and calls Crema (or a Wire table component). Column cell renderers may stay small Kotlin snippets until field/column views exist.

### Phase D — Form field wrappers

Built-in fields: keep fluent API; move label/hint/prefix/suffix wrapper chrome to Crema. Align with grind scaffolds (`view("firefly.fields…")`). Schema `renderBody` already prefers named views when set.

## Coverage and API

- Public Kotlin/Java APIs (`Panel.layout`, `overridePartial`, `Component.view`, table fluent API) stay stable where possible; change **how** HTML is produced, not the config surface.
- New Crema paths are production code: load them in tests, assert markers and key classes, keep **100%** line + branch on touched modules.
- Do not claim performance wins for either side without a bench (Wire has `modules/wire/.../bench/WireBench.kt` as a pattern).

## Related docs

- Parity ledger: [firefly-filament-parity.md](firefly-filament-parity.md)
- Wire / Livewire ledger: [livewire-v4-parity.md](livewire-v4-parity.md)
- Handoff: [HANDOFF.md](HANDOFF.md)
- Public chrome guide: `apps/docs` → Firefly → Panel chrome
