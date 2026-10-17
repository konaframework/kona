# Public documentation site

Stack: **Astro + Starlight + `@almasix/starlight-theme`**, pnpm workspace, **Cloudflare Pages** (primary at `*.konaframework.org`), GitHub Pages project sites as a secondary mirror.

| Path | Role |
|------|------|
| `apps/docs/` | Public Starlight site → [docs.konaframework.org](https://docs.konaframework.org) (Cloudflare Pages). Mirror: [konaframework.github.io/kona](https://konaframework.github.io/kona/) |
| `docs/architecture/` | In-repo architecture / agent plan (not the public site) |
| `docs/quality/` | Milestone audits |
| `docs/governance/` | Process locks (this file, rulesets) |

### Site URL map

| URL | Role |
|-----|------|
| `/` (`apps/docs/src/pages/index.astro`) | **Marketing landing** — brand-first Astro page (not Starlight chrome). Styles in `apps/docs/src/styles/landing.css`. Light + dark via `html[data-theme]`, synced with Starlight’s `localStorage` key `starlight-theme`. |
| `/docs/` (`src/content/docs/docs/index.mdx`) | **Documentation hub** — Starlight splash into guides and reference. |

Respect `PUBLIC_BASE_PATH` (e.g. `/kona/` on GitHub Pages) for asset and CTA links on the landing page. Theme preference on `/` must match `/docs/` (same storage key; effective values `light` / `dark`).

Cursor rule: [`.cursor/rules/kona-docs.mdc`](../../.cursor/rules/kona-docs.mdc) (globs `apps/docs/**`).

## Brand (locked)

Coffee palette shared with Brew (Mocha / espresso roast). Dark headings use soft cream, not pure white.

| Token | Light | Dark |
|-------|-------|------|
| Primary | Mocha `#6F4E37` | Espresso roast `#A67C52` |
| Primary soft | `#8B6B4F` | `#C4A574` |
| Primary deep | `#4A3423` | `#8B6340` |
| Accent | Deep espresso `#3D2914` | Soft cream `#E8D4B8` |
| Accent soft | `#5C4033` | `#F0E6D6` |
| Accent deep | `#2A1C10` | `#D4B896` |
| Surface | Cream `#FAF6F0` | Espresso `#1A120C` |
| Heading | `#2A1C10` | Soft cream `#E8D4B8` |

CSS: `apps/docs/src/styles/custom.css` and `landing.css` (`--kona-primary`, `--kona-accent`, cream/espresso surfaces). Mark: Bean-K in `apps/docs/public/branding/`.

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

   **Sidebar IA (public):** group pages in progressive sections — *Getting started* → *The basics* → *Crema* → *Digging deeper* → *Security* → *Database* → *Frontend* → *Testing* → *Packages & tooling* — in `apps/docs/sidebar.mjs`. Prefer regrouping the sidebar over mass file moves so slugs stay stable.

6. **Internal vs public.** Component maps that exist for agents under `docs/architecture/` stay out of the public site unless rewritten in Kona-only language.

## Concurrent with milestones

Public guides for a milestone’s API surface are written **or rewritten** **in the same wave** as the code. Do not defer docs; do not treat early low-level pages as frozen.

- Expand `apps/docs/sidebar.mjs` (into the progressive groups above) and `src/content/docs/` when new APIs land.
- Kotlin and Java examples on public API pages.
- `pnpm --filter @kona/docs build` (or `cd apps/docs && pnpm build`) before merging docs-touching PRs.

## Deploy

### Hostnames (locked)

| Host | Repo | Cloudflare project |
|------|------|--------------------|
| [docs.konaframework.org](https://docs.konaframework.org) | `konaframework/kona` (`apps/docs`) | `kona` |
| [brew.konaframework.org](https://brew.konaframework.org) | `konaframework/kona-brew` | `kona-brew-docs` |
| [inertia.konaframework.org](https://inertia.konaframework.org) | `konaframework/kona-inertia` | `kona-inertia-docs` |

Production builds use `PUBLIC_SITE_URL` = that host and `PUBLIC_BASE_PATH=/` (apex path, not a GitHub project subpath).

### Cloudflare Workers Builds (primary)

**First publish does not require GitHub secrets.** Prefer Cloudflare’s Git integration (Workers Builds) so `docs.konaframework.org` goes live, then optionally harden with Wrangler secrets later.

Kona docs is a **static Astro site on Workers Assets** (not classic Pages). Config uses `"assets": { "directory": "./dist" }` and deploys with **`wrangler deploy`**. Do **not** use `pages_build_output_dir` or `wrangler pages deploy` for this project.

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
   | **Deploy command** (production) | `npx wrangler deploy` (reads root `wrangler.jsonc` → `apps/docs/dist`) |
   | Preview / non-production deploy | Prefer `npx wrangler deploy` if the dashboard allows it. If Cloudflare forces `npx wrangler preview`, that works only when Wrangler has `"previews": {}` (already in root and `apps/docs` configs). |
   | Environment / build variables | `PUBLIC_SITE_URL=https://docs.konaframework.org`, `PUBLIC_BASE_PATH=/`, `NODE_VERSION=22` |
   | Optional | `PNPM_VERSION=9.15.0` (matches root `packageManager`; Corepack also reads that field) |

   **Production path (recommended):** Deploy command = `npx wrangler deploy`. That keeps Workers Assets (`"assets": { "directory": "…" }`) and ships to the production Worker.

   **If the dashboard runs `npx wrangler preview`:** Wrangler errors with *Your Wrangler configuration is missing a `previews` block* unless the config includes `"previews": {}`. Both [`wrangler.jsonc`](../../wrangler.jsonc) and [`apps/docs/wrangler.jsonc`](../../apps/docs/wrangler.jsonc) include that block so preview and production paths work.

   **Wrong (build):** `npm ci && npm run build` — no `package-lock.json` in this monorepo.

   **Wrong (deploy):** `npx wrangler pages deploy …` — this project is Workers Assets, not Pages. Also avoid a Pages-style `pages_build_output_dir` in Wrangler; use `"assets": { "directory": "…" }`. Without a root `wrangler.jsonc` pointing at `apps/docs/dist`, default `npx wrangler deploy` fails with *Missing entry-point to Worker script or to assets directory*. GitHub check name: **Workers Builds: kona**.

5. Custom domain: `docs.konaframework.org` on the Worker (Workers & Pages → `kona` → Custom domains). Sister hosts `brew.` / `inertia.` stay on their own projects.
6. Push to `main` (or **Retry build** from the Cloudflare dashboard after saving settings).

Sister repos (**npm** + classic Pages-style wrangler) — see their READMEs / `docs/governance/docs-site.md`:

| Repo | Root directory | Build command | Output / deploy |
|------|----------------|---------------|-----------------|
| `kona-brew` | `apps/docs` | `npm ci && npm run build` | `dist` (+ Pages/`wrangler pages deploy` as configured) |
| `kona-inertia` | `apps/docs` | `npm ci && npm run build` | `dist` (+ Pages/`wrangler pages deploy` as configured) |

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
