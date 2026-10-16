# Public documentation site

Stack: **Astro + Starlight + `@almasix/starlight-theme`**, pnpm workspace, Cloudflare Workers static assets (`wrangler`), GitHub Pages project site.

| Path | Role |
|------|------|
| `apps/docs/` | Public Starlight site → [konaframework.dev](https://konaframework.dev) and [konaframework.github.io/kona](https://konaframework.github.io/kona/) |
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

| Token | Hex |
|-------|-----|
| Primary | `#FF9A00` |
| Accent | `#286291` |

CSS: `apps/docs/src/styles/custom.css` (`--kona-primary`, `--kona-accent`).

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

**Cloudflare (konaframework.dev):**

```bash
pnpm pages:build
pnpm --filter @kona/docs cf:deploy
```

**GitHub Pages (konaframework.github.io/kona):** workflow [`.github/workflows/pages.yml`](../../.github/workflows/pages.yml) builds with `PUBLIC_BASE_PATH=/kona/` on pushes to `main` that touch docs. Enable **Settings → Pages → Source: GitHub Actions** once per repo (human; agents must not change rulesets).

```bash
pnpm pages:gh   # local parity build for the /kona/ base path
```
