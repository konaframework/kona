# Firefly / Filament parity ledger

Source of truth for behavior: Filament 5 (Filament 4 panel API on Livewire 4). Kona names the product **Firefly**. This ledger is for agents. Public docs must not compare Firefly to Filament.

Status: `todo` · `done`. A row is `done` only when the method, test, and public doc exist in this repo.

M28 is the support slice. Later rows stay `todo` until their milestone is the active one and has merged.

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

## Queued

| Milestone | Filament packages | Firefly artifact | Status |
|-----------|-------------------|------------------|--------|
| M29 | `filament/schemas` reactivity on the base field | `kona-firefly-schemas` | todo |
| M30 | `filament/actions` | `kona-firefly-actions` | todo |
| M31 | `filament/forms` | `kona-firefly-forms` | todo |
| M32 | `filament/infolists` | `kona-firefly-infolists` | todo |
| M33 | `filament/tables` | `kona-firefly-tables` | todo |
| M34 | notifications + widgets (Chart.js and ApexCharts) | `kona-firefly-notifications`, `kona-firefly-widgets` | todo |
| M35 | panel, auth, navigation (`App` / `Side` / `Top`, depth 3) | `kona-firefly` | todo |
| M36 | resources, including `--simple` | `kona-firefly` | todo |
| M37 | tenancy, plugins, import/export, `Firefly.test`, `kona new --kit=firefly` | `kona-firefly` | todo |
| M38 | IDE catalog after M37 is on `main` | `kona-lsp`, editor extensions | todo |
| Document addon | Mongo / `Document<T>` queries, after M27 and M37 | `kona-firefly-document` | todo |
