# Firefly / Filament parity ledger

Source of truth for behavior: Filament 5 (Filament 4 panel API on Livewire 4). Kona names the product **Firefly**. This ledger is for agents. Public docs must not compare Firefly to Filament.

Status: `todo` · `done`. A row is `done` only when the method, test, and public doc exist in this repo.

M28 and M29 are on `main`. M31 is the forms slice. A helper that needs the action type, a record store, or a table stays `todo` even when the rest of M31 is `done`.

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
| FRM-18 | `prefixAction`, `suffixAction`, `hintAction` | needs `Action` (M30) | todo |
| FRM-19 | Repeater `addAction` / `deleteAction` / `cloneAction` / `extraItemActions` as actions | needs `Action` (M30). Plain buttons ship in FRM-14 | todo |
| FRM-20 | Builder `addBetweenAction` | needs `Action` (M30) | todo |
| FRM-21 | Rich-editor custom block edit as an action | needs `Action` (M30). `Dialog` is used directly | todo |
| FRM-22 | Select `createOptionAction` | needs `Action` (M30). `createOptionForm` and `createOptionUsing` ship in FRM-7 | todo |
| FRM-23 | `relationship` record query / `modifyQueryUsing` | needs the record store (M33 / M36). Name and title attribute are stored | todo |
| FRM-24 | `ModalTableSelect` | needs tables (M33) | todo |
| FRM-25 | `MorphToSelect` | needs models / record store | todo |
| FRM-26 | File upload browse / file manager | later package. Upload and `commit` ship in FRM-13 | todo |

## Queued

| Milestone | Filament packages | Firefly artifact | Status |
|-----------|-------------------|------------------|--------|
| M30 | `filament/actions` | `kona-firefly-actions` | todo |
| M31 | `filament/forms` | `kona-firefly-forms` | done (FRM-18–FRM-26 stay todo) |
| M32 | `filament/infolists` | `kona-firefly-infolists` | todo |
| M33 | `filament/tables` | `kona-firefly-tables` | todo |
| M34 | notifications + widgets (Chart.js and ApexCharts) | `kona-firefly-notifications`, `kona-firefly-widgets` | todo |
| M35 | panel, auth, navigation (`App` / `Side` / `Top`, depth 3) | `kona-firefly` | todo |
| M36 | resources, including `--simple` | `kona-firefly` | todo |
| M37 | tenancy, plugins, import/export, `Firefly.test`, `kona new --kit=firefly` | `kona-firefly` | todo |
| M38 | IDE catalog after M37 is on `main` | `kona-lsp`, editor extensions | todo |
| Document addon | Mongo / `Document<T>` queries, after M27 and M37 | `kona-firefly-document` | todo |
