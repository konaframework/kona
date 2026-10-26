# Firefly / Filament parity ledger

Source of truth for behavior: Filament 5 (Filament 4 panel API on Livewire 4). Kona names the product **Firefly**. This ledger is for agents. Public docs must not compare Firefly to Filament.

Status: `todo` · `done`. A row is `done` only when the method, test, and public doc exist in this repo.

M28 is on `main`. M29 is the active schemas slice. Later rows stay `todo` until their milestone is the active one and has merged.

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

## Queued

| Milestone | Filament packages | Firefly artifact | Status |
|-----------|-------------------|------------------|--------|
| M30 | `filament/actions` | `kona-firefly-actions` | todo |
| M31 | `filament/forms` | `kona-firefly-forms` | todo |
| M32 | `filament/infolists` | `kona-firefly-infolists` | todo |
| M34 | notifications + widgets (Chart.js and ApexCharts) | `kona-firefly-notifications`, `kona-firefly-widgets` | todo |
| M35 | panel, auth, navigation (`App` / `Side` / `Top`, depth 3) | `kona-firefly` | todo |
| M36 | resources, including `--simple` | `kona-firefly` | todo |
| M37 | tenancy, plugins, import/export, `Firefly.test`, `kona new --kit=firefly` | `kona-firefly` | todo |
| M38 | IDE catalog after M37 is on `main` | `kona-lsp`, editor extensions | todo |
| Document addon | Mongo / `Document<T>` queries, after M27 and M37 | `kona-firefly-document` | todo |
