# Firefly / Filament parity ledger

Source of truth for behavior: Filament 5 (Filament 4 panel API on Livewire 4). Kona names the product **Firefly**. This ledger is for agents. Public docs must not compare Firefly to Filament.

Status: `todo` · `done`. A row is `done` only when the method, test, and public doc exist in this repo.

M28, M29, M30, and M32 are on `main`. M33 is the tables slice. Later rows stay `todo` until their milestone has shipped.

## M28 — support (`kona-firefly-support`)

| ID | Filament surface | Firefly | Status |
|----|------------------|---------|--------|
| SUP-1 | Panel theme colors, font, radius | `FireflyTheme` + `ThemeTokens` + `KonaTheme` | done |
| SUP-2 | Scoped panel CSS, no host preflight | `.ff-panel` stylesheet from Tailwind v4 source | done |
| SUP-3 | Render hooks and layout views | `PanelShell` partials, `Hook`, `PanelLayout` | done |
| SUP-4 | App view overrides a package view | `overridePartial` wins over `FireflyTheme.partial` | done |
| SUP-5 | Modal, slide-over, fullscreen | `Dialog.modal` / `slideOver` / `slideOverStart` / `fullscreen` | done |
| SUP-6 | `configureUsing` on the component | `Dialog.configureUsing` | done |

Out of M28 (do not implement on the M28 branch): schemas, fields, actions, forms, infolists, tables, notifications, widgets, panel routes, resources, tenancy, plugins, import/export, the document addon, IDE catalog.

## M29 — schemas (`kona-firefly-schemas`)

| ID | Surface | Firefly | Status |
|----|---------|---------|--------|
| SCH-1 | Schema tree, state path, hydrate / dehydrate | `Schema` | done |
| SCH-2 | Relative Get / Set | `Get`, `Set` | done |
| SCH-3 | Layout components | `Grid`, `Flex`, `Fieldset`, `Section`, `Tabs`, `Wizard`, `Callout`, `EmptyState` | done |
| SCH-4 | Prime components | `Text`, `SchemaIcon`, `Image`, `UnorderedList` | done |
| SCH-5 | Static value or closure on chainable setters | `label`, `heading`, `description`, `hidden`, `visible`, `columns`, `content`, and the same shape of setter | done |
| SCH-6 | Live modifiers and partial render | `live`, `liveOnBlur`, `liveDebounce`, `afterStateUpdated` | done |
| SCH-7 | Alpine factories and Crema view | `ffField`, `ffEntry`, `ffColumn`, `ffAction`, `Schema.crema` | done |
| SCH-8 | Layout scaffold | `make:firefly-layout` | done |

M31 notes (do not implement in M29): `Select` defaults to a combobox, and `.native()` opts into `<select>`. Date and time pickers default to Firefly’s crafted picker, and `.native()` opts into the HTML date/time input.

## M30 — actions (`kona-firefly-actions`)

| ID | Surface | Firefly | Status |
|----|---------|---------|--------|
| ACT-1 | `Action` / `ActionGroup`, `make`, `configureUsing` | `Action`, `ActionGroup` | done |
| ACT-2 | Static value or `Get` closure | `label`, `icon`, `color`, `tooltip`, `badge`, `badgeColor`, `size`, `iconPosition`, `url`, `openUrlInNewTab`, `requiresConfirmation`, modal copy, `modalWidth`, redirect URLs | done |
| ACT-3 | Visibility, disabled, authorization | `hidden`, `visible`, `disabled`, `authorize` boolean / predicate / ability + `Action.gate` | done |
| ACT-4 | Icon, color, tooltip, badge, size, button styles, extra attributes | rendered on the trigger | done |
| ACT-5 | Confirmation and modal copy | heading, description, submit, cancel, icon, close, autofocus, escape, overlay click | done |
| ACT-6 | Mount `Dialog` | `modal`, `slideOver`, `slideOverStart`, `fullscreen`, Dialog widths, `modalDraggable` | done |
| ACT-7 | URL actions | `url`, `openUrlInNewTab` | done |
| ACT-8 | Modal schema content | `schema(...)` with layouts and primes | done |
| ACT-9 | Schema and header placement | `ActionPlacement.SCHEMA`, `ActionPlacement.HEADER` | done |
| ACT-10 | Server call and local open state | `wire:click`, `ffAction`, Alpine `open` | done |
| ACT-11 | Handler lifecycle | `action`, `before`, `after`, `halt`, `failure`, redirect URLs, `arguments` | done |
| ACT-12 | Action scaffold | `make:firefly-action`, Kotlin and `--lang=java` | done |

Shipped modal content is the schema layouts and primes already on `main`. These Filament action helpers wait on a later milestone. They are not stubbed here:

| Helper | Waits on |
|--------|----------|
| Form fields inside an action, `fillForm`, form data mutation, a disabled form, action `steps()` as a form wizard | M31 |
| Infolist actions | M32 |
| Record, bulk, and toolbar table actions | M33 |
| Success and failure notifications | M34 |
| Import and export actions | M37 |
| Parent-dialog cancellation, sticky header/footer, modal alignment, extra window attributes, widths outside `sm`/`md`/`lg`/`xl`/`screen`, database transactions, rate limits | `Dialog` does not expose them, or no rate-limit/transaction API is wired into this module |

## M33 — tables (`kona-firefly-tables`)

| ID | Surface | Firefly | Status |
|----|---------|---------|--------|
| TBL-1 | Columns | `TextColumn`, `IconColumn`, `ImageColumn`, `ColorColumn`, `CheckboxColumn`, `ToggleColumn`, `SelectColumn` | done |
| TBL-2 | Filters | `Filter`, `SelectFilter`, `TernaryFilter`, `TrashedFilter` | done |
| TBL-3 | Search, sort, pagination, empty state | `search`, `sort`, `page`, `perPage`, `empty` | done |
| TBL-4 | Row selection state | `selectable`, `select`, `selected` | done |
| TBL-5 | Summaries | `summarize` `COUNT`, `SUM`, `AVG`, `MIN`, `MAX` | done |
| TBL-6 | Grouping, reorder, stack and grid, polling | `groups`, `reorderable`, `TableLayout`, `poll` | done |
| TBL-7 | Static value or closure on chainable setters | `label`, `hidden`, `visible`, `default` / `defaultUsing`, and the same shape of setter | done |
| TBL-8 | Native query hook | `modifyQueryUsing` receives `ModelBuilder` for a Cadence model (`whereHas`, `join`, `with`, `scope`) and `MemoryQuery` for an in-memory list | done |
| TBL-9 | Row and header buttons | `TableButton` renders through `Dialog` and a schema. `label` accepts a string or a closure | done |
| TBL-10 | Alpine column factory | `ffColumn` with `@entangle` and `$wire.$call` | done |
| TBL-11 | Scaffolds | `make:firefly-column`, `make:firefly-filter`, Kotlin and `--lang=java` | done |
| TBL-12 | Typed action objects | Attach when M30 (`kona-firefly-actions`) is on `main`. Buttons stay `TableButton` until then | todo |

## M32 — infolists (`kona-firefly-infolists`)

Depends on `kona-firefly-schemas` and `kona-firefly-support` only. Layouts are schema `Section`, `Grid`, `Tabs`, and `Tab`. The code entry calls Shiki `createHighlighter` in the browser.

| ID | Surface | Firefly | Status |
|----|---------|---------|--------|
| INF-1 | Infolist over a schema | `Infolist` | done |
| INF-2 | Text entry | `TextEntry` | done |
| INF-3 | Icon entry | `IconEntry` (`bool`, `whenTrue`, `whenFalse`) | done |
| INF-4 | Image entry | `ImageEntry` (disk, visibility, and file check are data attributes) | done |
| INF-5 | Color entry | `ColorEntry` | done |
| INF-6 | Key-value entry | `KeyValueEntry` | done |
| INF-7 | Code entry | `CodeEntry` + `ShikiCode` (`createHighlighter`) | done |
| INF-8 | Repeatable entry | `RepeatableEntry`, `TableColumn` | done |
| INF-9 | Shared entry helpers and static-or-closure setters | `Entry` | done |
| INF-10 | Entry scaffold | `make:firefly-entry` | done |

Left for later milestones (not implemented here): prefix and suffix actions that need `Action` (M30); record aggregates; private-disk temporary URLs; the forms code editor (M31); tables (M33).

## Queued

| Milestone | Filament packages | Firefly artifact | Status |
|-----------|-------------------|------------------|--------|
| M30 | `filament/actions` | `kona-firefly-actions` | done |
| M31 | `filament/forms` | `kona-firefly-forms` | todo |
| M32 | `filament/infolists` | `kona-firefly-infolists` | done |
| M33 | `filament/tables` | `kona-firefly-tables` | done |
| M34 | notifications + widgets (Chart.js and ApexCharts) | `kona-firefly-notifications`, `kona-firefly-widgets` | todo |
| M35 | panel, auth, navigation (`App` / `Side` / `Top`, depth 3) | `kona-firefly` | todo |
| M36 | resources, including `--simple` | `kona-firefly` | todo |
| M37 | tenancy, plugins, import/export, `Firefly.test`, `kona new --kit=firefly` | `kona-firefly` | todo |
| M38 | IDE catalog after M37 is on `main` | `kona-lsp`, editor extensions | todo |
| Document addon | Mongo / `Document<T>` queries, after M27 and M37 | `kona-firefly-document` | todo |
