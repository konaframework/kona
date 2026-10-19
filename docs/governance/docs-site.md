# Public documentation site

Stack: **Astro + Starlight + `@almasix/starlight-theme`**, pnpm workspace, **Cloudflare Workers Assets** (primary at `*.konaframework.org`), GitHub Pages project sites as a secondary mirror.

| Path | Role |
|------|------|
| `apps/docs/` | Public Starlight site → [docs.konaframework.org](https://docs.konaframework.org) (Cloudflare Worker `kona`). Mirror: [konaframework.github.io/kona](https://konaframework.github.io/kona/) |
| `docs/architecture/` | In-repo architecture / agent plan (not the public site) |
| `docs/quality/` | Milestone audits |
| `docs/governance/` | Process locks (this file, rulesets) |

### Site URL map

| URL | Role |
|-----|------|
| `/` (`apps/docs/src/pages/index.astro`) | **Marketing landing** — brand-first Astro page (not Starlight chrome). Styles in `apps/docs/src/styles/landing.css`. Light + dark via `html[data-theme]`, synced with Starlight’s `localStorage` key `starlight-theme`. |
| `/docs/` (`src/content/docs/docs/index.mdx`) | **Documentation hub** — Starlight splash into guides and reference. |

Respect `PUBLIC_BASE_PATH` (e.g. `/kona/` on GitHub Pages) for asset and CTA links on the landing page. Theme preference on `/` must match `/docs/` (same storage key; effective values `light` / `dark`). The landing page rewrites `/docs/…` CTAs to locale-prefixed paths (`/es/docs/…`, `/fr/docs/…`, `/zh-cn/docs/…`) when `navigator.language` matches.

Cursor rule: [`.cursor/rules/kona-docs.mdc`](../../.cursor/rules/kona-docs.mdc) (globs `apps/docs/**`).

## Versions (`starlight-versions`)

Plugin: [`starlight-versions`](https://starlight-versions.vercel.app/) in `apps/docs/astro.config.mjs`.

| UI label | Role | Content |
|----------|------|---------|
| **`{major}.x (Latest)`** (default / current) | Published release line — same docs as the current `kona.version` major (e.g. `0.1.1` → **0.x (Latest)**). Unversioned URLs. | `src/content/docs/` (root English + locale trees) |
| **Dev** (`main` slug) | In-progress tip archive | `src/content/docs/main/` + `src/content/versions/main.json` |

Do **not** add a separate picker entry named “latest”. Latest *is* the current major line. When a new major ships, freeze the prior major under its slug (e.g. `0`), keep current labeled `{newMajor}.x (Latest)`, and re-seed.

Seed archives with `cd apps/docs && node scripts/seed-versions.mjs` (plugin allows only one *new* version per build). Version picker is injected via almasixTheme `headerExtras` → `src/components/VersionSelect.astro` (theme already owns `ThemeSelect`).

Collection: `versions` loader in `src/content.config.ts`. Sidebar stays in `sidebar.mjs`; archived copies store a snapshot in each `versions/*.json`.

## Internationalization

Root locale is **English** (no `/en/` prefix). Additional locales: **`es`**, **`fr`**, **`zh-cn`** (`lang: zh-CN`).

- UI strings: `src/content/i18n/{es,fr,zh-CN}.json` (Starlight + starlight-versions keys).
- Translated content today: Getting started **Introduction** + **Installation** under `src/content/docs/{es,fr,zh-cn}/docs/guides/`. Other pages fall back to English with Starlight’s untranslated notice.
- Sidebar Getting started labels carry `translations` for those locales.

## GitHub header chip

`@almasix/starlight-theme` fetches stars / forks / latest release **at build time** (SSR placeholder; may show `—` when the API is rate-limited).

Client hydrate: `public/scripts/gh-chip-hydrate.js` is injected via Starlight `head` and refreshes `.gh-chip` from `api.github.com` with **sessionStorage** cache (**10 minutes**). Keep the SSR markup; do not remove the theme’s build-time fetch.

Optional Worker proxy `/api/github-stats` is **not** wired yet (docs Worker is assets-only today). Prefer the client script unless a Worker `main` entry is added later.

## Brand (locked)

Coffee palette shared with Brew and Inertia (caramel / latte). Dark headings use soft cream, not pure white. Light accent is warm brown `#74453F` (~7.3:1 on cream) for links, feature headings, and secondary emphasis; primary deep `#9A5812` remains for caramel shade steps. Buttons on caramel fills use espresso `#1A120C` text. Body/heading ink stays deep espresso for long-form readability.

| Token | Light | Dark |
|-------|-------|------|
| Primary | Caramel `#DF8723` | Latte `#C9A57B` |
| Primary soft | `#E9A055` | `#DCC09A` |
| Primary deep | `#9A5812` | `#A67C52` |
| Accent | Warm brown `#74453F` | Soft cream `#E8D4B8` |
| Accent soft | `#8B5A52` | `#F0E6D6` |
| Accent deep | `#5A322E` | `#D4B896` |
| Surface | Cream `#FAF6F0` | Espresso `#1A120C` |
| Heading | `#2A1C10` | Soft cream `#E8D4B8` |

CSS: `apps/docs/src/styles/custom.css` and `landing.css` (`--kona-primary`, `--kona-accent`, cream/espresso surfaces). **Square mark:** brew mug paths verbatim as `kona-logo.svg` (caramel `#DF8723` light; cream/white fill dark — same `d`). **Banner:** compose `[mark] · kona` monochrome (espresso on light, white on dark) — do not reshape mark paths. Accent is CSS/theme only. Sister product mirrors under `apps/docs/public/branding/`: `brew-*` and `inertia-*` for reference.

## Voice and depth (locked)

1. **Audience.** Readers know programming and common web ideas (HTTP methods, JSON, packages, classes). They do **not** necessarily know any particular web framework’s jargon. Define every Kona concept (application, provider, container, middleware, grind, …) in plain language the first time it appears. Walk through *why* it exists and *when* it runs.

2. **Independence.** Inspiration from other ecosystems may get a **single short nod** in the introduction. After that, public docs must **not** compare Kona to other frameworks, list “equivalents,” or explain features as “like X.” Kona is documented as its own product: Kona names, Kona APIs, Kona behavior.

3. **Depth by section type.**

   | Section kind | Standard |
   |--------------|----------|
   | **Getting started** | Minimal, linear, no-brainer path. No advanced config, no exhaustive API catalogs, no complex middleware/provider deep-dives. |
   | **Feature guides** (e.g. *The basics → Routing*) | **Exhaust** the held section: concepts, full shipped public API, use cases, edge cases, progressive Kotlin **and** Java examples, and failure behavior. Target depth of a mature framework feature guide — without comparing frameworks on the public page. Document only what exists; omit invented APIs. |

4. **Detail (feature guides).** Prefer thorough guides over thin stubs. Include lifecycle notes, defaults, error behavior, named helpers, and Kotlin **plus** Java examples for public surfaces. Prefer many progressive examples over one mega-snippet.

5. **Progressive path (trajectory).** Today’s public site still leans on low-level application wiring (explicit providers, registries). That is honest for the current API, but it is **not** the end state. As scaffolding and conventions land, **rewrite** guides so newcomers start with the simple path (*Getting started*) and only dig into providers/container internals later — developer-friendly progressive disclosure, not a disjoint dump of every knob on page one.

   **Sidebar IA (public):** group pages in progressive sections — *Getting started* → *The basics* → *Crema* → *Digging deeper* → *Security* → *Database* → *Frontend* → *Brew* → *Inertia* → *Testing* → *Packages & tooling* — in `apps/docs/sidebar.mjs`. Brew and Inertia are top-level groups (not nested under Frontend). Prefer regrouping the sidebar over mass file moves so slugs stay stable.

6. **Internal vs public.** Component maps that exist for agents under `docs/architecture/` stay out of the public site unless rewritten in Kona-only language.

## Concurrent with milestones

Public guides for a milestone’s API surface are written **or rewritten** **in the same wave** as the code. Do not defer docs; do not treat early low-level pages as frozen.

- Expand `apps/docs/sidebar.mjs` (into the progressive groups above) and `src/content/docs/` when new APIs land.
- Kotlin and Java examples on public API pages.
- `pnpm --filter @kona/docs build` (or `cd apps/docs && pnpm build`) before merging docs-touching PRs.

## Deploy

### Hostnames (locked)

| Host | Repo path | Cloudflare project |
|------|-----------|--------------------|
| [docs.konaframework.org](https://docs.konaframework.org) | `konaframework/kona` (`apps/docs`, pnpm) | `kona` |

Brew and Inertia guides live **inside** this site:

| Section | Canonical path |
|---------|----------------|
| Brew | [docs.konaframework.org/docs/brew/](https://docs.konaframework.org/docs/brew/) |
| Inertia | [docs.konaframework.org/docs/inertia/](https://docs.konaframework.org/docs/inertia/) |

Stub READMEs only: `apps/brew-docs/`, `apps/inertia-docs/` (no separate Starlight builds).

Production builds use `PUBLIC_SITE_URL=https://docs.konaframework.org` and `PUBLIC_BASE_PATH=/` (apex path, not a GitHub project subpath).

### Cloudflare Workers Builds (primary)

**First publish does not require GitHub secrets.** Prefer Cloudflare’s Git integration (Workers Builds) so `docs.konaframework.org` goes live, then optionally harden with Wrangler secrets later.

Kona docs is a **static Astro site on Workers Assets** (not classic Pages). Config uses `"assets": { "directory": "…" }` plus `"previews": {}`, and deploys with **`npx wrangler deploy`**. This project does **not** use `pages_build_output_dir`.

#### Do not use — classic Pages deploy (auth error 10000)

Project **`kona`** was migrated to **Workers Assets**. Dashboard / CI must **not** call the Pages API for it.

| | Use this | Never use this |
|---|----------|----------------|
| Deploy | `npx wrangler deploy` | `npx wrangler pages deploy …` |
| Flags | (none required; name comes from `wrangler.jsonc`) | `--project-name=kona` with `pages deploy` |
| Config key | `"assets": { "directory": "…" }` | `pages_build_output_dir` |

**Why the log fails:** `wrangler pages deploy apps/docs/dist --project-name=kona` talks to `/pages/projects/kona`. Wrangler then warns *missing pages_build_output_dir … Ignoring configuration file* and hits **Authentication error [code: 10000]** — the token/path is for Pages, but `kona` is a Workers Assets project (or the token lacks Pages edit for a non-Pages project). Fix the **Deploy command** in the dashboard; do not chase Pages tokens.

**If someone insists on classic Pages:** create a **separate** Pages project, set `pages_build_output_dir`, and use `wrangler pages deploy` there. We did **not** choose that path for `kona` — stick to Workers Assets + `npx wrangler deploy`.

#### Path A — Workers Builds Git integration (recommended for first publish)

No `CLOUDFLARE_API_TOKEN` / `CLOUDFLARE_ACCOUNT_ID` in GitHub. Cloudflare clones and builds from the repo.

1. Cloudflare account on the `konaframework.org` zone.
2. **Workers & Pages →** project **`kona`** → **Settings → Builds** (or Create → Connect to Git → `konaframework/kona`).
3. **Framework preset: None** (do **not** pick Astro — that preset injects `npm ci` / `npm run build`).
4. Build settings for `kona` (**pnpm** workspace at repo root — lockfile is `pnpm-lock.yaml`; there is **no** `package-lock.json`):

   | Setting | Value |
   |---------|--------|
   | Root directory | `/` (repo root — **required**; do not set `apps/docs`) |
   | Build command | `pnpm install --frozen-lockfile && pnpm docs:build` |
   | **Deploy command** (production) | `npx wrangler deploy` |
   | Preview / non-production deploy | Prefer `npx wrangler deploy` if the dashboard allows it. If Cloudflare forces `npx wrangler preview`, that works only when Wrangler has `"previews": {}` (already in root and `apps/docs` configs). |
   | Environment / build variables | `PUBLIC_SITE_URL=https://docs.konaframework.org`, `PUBLIC_BASE_PATH=/`, `NODE_VERSION=22` |
   | Optional | `PNPM_VERSION=9.15.0` (matches root `packageManager`; Corepack also reads that field) |

   **Production path (required):** Deploy command = `npx wrangler deploy` only. That reads root [`wrangler.jsonc`](../../wrangler.jsonc) → `assets.directory: ./apps/docs/dist` and ships the production Worker. GitHub check name: **Workers Builds: kona**.

   **If the dashboard runs `npx wrangler preview`:** Wrangler errors with *Your Wrangler configuration is missing a `previews` block* unless the config includes `"previews": {}`. Both [`wrangler.jsonc`](../../wrangler.jsonc) and [`apps/docs/wrangler.jsonc`](../../apps/docs/wrangler.jsonc) include that block so preview and production paths work.

   **Wrong (build):** `npm ci && npm run build` — no `package-lock.json` in this monorepo.

   **Wrong (deploy):** any `npx wrangler pages deploy …` / `--project-name=kona` — see **Do not use** above. Without a root `wrangler.jsonc` pointing at `apps/docs/dist`, bare `npx wrangler deploy` fails with *Missing entry-point to Worker script or to assets directory*.

5. Custom domain: `docs.konaframework.org` on the Worker (Workers & Pages → `kona` → Custom domains).
6. **Save** Build settings, then **Retry build** (or push to `main`). Settings-only — no need to wait for a repo merge if the Deploy command was the only wrong field.

#### Human Cloudflare follow-up — retire Brew / Inertia hosts

Separate Workers `kona-brew-docs` / `kona-inertia-docs` and hosts `brew.konaframework.org` / `inertia.konaframework.org` are **deprecated**. Content ships only from `apps/docs`.

Suggested redirects (Cloudflare Bulk Redirects, Worker routes, or DNS + Worker):

| From | To |
|------|----|
| `https://brew.konaframework.org/` | `https://docs.konaframework.org/docs/brew/` |
| `https://brew.konaframework.org/*` | `https://docs.konaframework.org/docs/brew/:splat` (or map old paths under `/guides/…` → `/docs/brew/guides/…`, `/essentials/…` → `/docs/brew/essentials/…`, etc.) |
| `https://inertia.konaframework.org/` | `https://docs.konaframework.org/docs/inertia/` |
| `https://inertia.konaframework.org/*` | `https://docs.konaframework.org/docs/inertia/:splat` (same prefix mapping) |

Then disable or delete Workers Builds for `kona-brew-docs` / `kona-inertia-docs` (do **not** change GitHub rulesets).

#### Path B — optional Wrangler from GitHub Actions (harden later)

Workflow: [`.github/workflows/cloudflare-pages.yml`](../../.github/workflows/cloudflare-pages.yml) — on `main` docs changes it **always builds** (smoke). It **deploys with Wrangler only when** the secrets below exist; otherwise it exits the deploy step cleanly (build-only notice). Harmless alongside Path A if you keep both.

| Secret | Purpose |
|--------|---------|
| `CLOUDFLARE_API_TOKEN` | API token with **Account → Workers Scripts → Edit** (and related Workers permissions) |
| `CLOUDFLARE_ACCOUNT_ID` | Cloudflare account id (dashboard sidebar / Workers & Pages) |

Local:

```bash
pnpm pages:cf          # build for docs.konaframework.org
pnpm --filter @kona/docs cf:deploy   # wrangler deploy (needs `wrangler login` or token env)
```

Config: root [`wrangler.jsonc`](../../wrangler.jsonc) (`assets.directory: ./apps/docs/dist`) for Workers Builds; [`apps/docs/wrangler.jsonc`](../../apps/docs/wrangler.jsonc) for local app-scoped deploy. Optional `public/_headers` ships with the static build.

### GitHub Pages (secondary mirror)

Workflow [`.github/workflows/pages.yml`](../../.github/workflows/pages.yml) still publishes to `konaframework.github.io/kona` with `PUBLIC_BASE_PATH=/kona/`. Enable **Settings → Pages → Source: GitHub Actions** once per repo (human; agents must not change rulesets).

```bash
pnpm pages:gh   # local parity build for the /kona/ base path
```
