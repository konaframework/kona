# Firefly / Filament parity ledger

Source of truth for behavior: Filament 5 (Filament 4 panel API on Livewire 4). Kona names the product **Firefly**. This ledger is for agents. Public docs must not compare Firefly to Filament.

Status: `todo` · `done`. A row is `done` only when the method, test, and public doc exist in this repo.

Verified on `origin/main` `0bd0c321` (including #219): `firefly-support` (M28), `firefly-schemas` (M29), `firefly-actions` (M30), `firefly-infolists` (M32), and `firefly-tables` (M33) are in the tree. M31 forms, including FRM-18–FRM-26, are complete on `milestone/M31-firefly-forms` and land on `main` when that PR merges. M34–M38 stay queued and not started. Law 1 still allows parallel non-colliding milestones.

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

## M31 — forms (`kona-firefly-forms`)

Shipped fields extend `Component` in this module. Schemas and support were not edited. Kotlin `.native()` is Java `useNative` because `native` is a Java keyword.

| ID | Filament surface | Firefly | Status |
|----|------------------|---------|--------|
| FRM-1 | Field chrome, value or closure | `Field` label, hint, prefix, suffix, placeholder, helper, above/below | done |
| FRM-2 | `required` / `nullable` / rules | `required`, `nullable`, `rule`, `validationAttribute`, `validationMessages` | done |
| FRM-3 | Dehydrate transforms | `formatStateUsing`, `dehydrateStateUsing`, `saved` | done |
| FRM-4 | Text inputs | `TextInput`, `Textarea`, `Hidden` | done |
| FRM-5 | Tags, color, slider, key-value | `TagsInput`, `ColorPicker`, `Slider`, `KeyValue` | done |
| FRM-6 | Placeholder and view | `Placeholder`, `ViewField` | done |
| FRM-7 | Select combobox, `.native()` for `<select>` | `Select` | done |
| FRM-8 | Checkbox, toggle, radio, checkbox list, toggle buttons | those classes | done |
| FRM-9 | Date and time, crafted picker, `.native()` for the HTML input, `java.time`, date-only in the app zone | `DatePicker`, `TimePicker`, `DateTimePicker`, `FieldClock` | done |
| FRM-10 | Rich editor, TipTap toolbar and bubble menu, dialog for link, image, and blocks | `RichEditor` | done |
| FRM-11 | Markdown Write \| Preview, CodeMirror markdown, sanitized `.ff-prose` | `MarkdownEditor` | done |
| FRM-12 | Code editor, CodeMirror 6 and the Shiki language catalog | `CodeEditor` | done |
| FRM-13 | File upload to `POST /_firefly/uploads`, FilePond image editor, filesystem commit | `FileUpload`, `FireflyUploads` | done |
| FRM-14 | Repeater and builder item state | `Repeater`, `Builder` | done |
| FRM-15 | Form validation walk | `FieldValidation` | done |
| FRM-16 | Live bindings, including editors and uploads | `live`, `liveOnBlur`, `liveDebounce`, `afterStateUpdated`, `@entangle` | done |
| FRM-17 | Field scaffold | `make:firefly-field` Kotlin and `--lang=java` | done |
| FRM-18 | `prefixAction`, `suffixAction`, `hintAction` | `Field` + `Action` | done |
| FRM-19 | Repeater `addAction` / `deleteAction` / `cloneAction` / `extraItemActions` as actions | `Repeater` + `Action` | done |
| FRM-20 | Builder `addBetweenAction` | `Builder` + `Action` | done |
| FRM-21 | Rich-editor custom block edit as an action | `RichEditor.editBlockAction` + `Action`. `Dialog` stays on the block | done |
| FRM-22 | Select `createOptionAction` | `Select` + `Action`. `createOptionForm` and `createOptionUsing` stay | done |
| FRM-23 | `relationship` record query / `modifyQueryUsing` | `Select.relationship` + `RecordStore` | done |
| FRM-24 | `ModalTableSelect` | `ModalTableSelect` + `Table` | done |
| FRM-25 | `MorphToSelect` | `MorphToSelect` + `RecordStore` | done |
| FRM-26 | File upload browse | `FileUpload.browseAction`, `browse`, `chooseBrowsed`. File manager stays a later package | done |

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
| TBL-12 | Typed action objects | Still open. M30 is on `main`. Buttons stay `TableButton` | todo |

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

Left for later milestones (not implemented in this module): prefix and suffix actions; record aggregates; private-disk temporary URLs; the forms code editor (M31, still not on `main`). Tables shipped in M33.

## Queued

| Milestone | Filament packages | Firefly artifact | Status |
|-----------|-------------------|------------------|--------|
| M30 | `filament/actions` | `kona-firefly-actions` | done |
| M31 | `filament/forms` | `kona-firefly-forms` | done (on `main` when the forms PR merges) |
| M32 | `filament/infolists` | `kona-firefly-infolists` | done |
| M33 | `filament/tables` | `kona-firefly-tables` | done |
| M34 | notifications + widgets (Chart.js and ApexCharts) | `kona-firefly-notifications`, `kona-firefly-widgets` | queued (not started) |
| M35 | panel, auth, navigation (`App` / `Side` / `Top`, depth 3) | `kona-firefly` | queued (not started) |
| M36 | resources, including `--simple` | `kona-firefly` | queued (not started) |
| M37 | tenancy, plugins, import/export, `Firefly.test`, `kona new --kit=firefly` | `kona-firefly` | queued (not started) |
| M38 | IDE catalog after M37 is on `main` | `kona-lsp`, editor extensions | queued (not started) |
| Document addon | Mongo / `Document<T>` queries, after M27 and M37 | `kona-firefly-document` | todo |
