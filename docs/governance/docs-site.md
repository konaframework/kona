# Public documentation site

Stack: **Astro + Starlight + `@almasix/starlight-theme`**, pnpm workspace, Cloudflare Workers static assets (`wrangler`), GitHub Pages project site.

| Path | Role |
|------|------|
| `apps/docs/` | Public Starlight site → [konaframework.dev](https://konaframework.dev) and [konaframework.github.io/kona](https://konaframework.github.io/kona/) |
| `docs/architecture/` | In-repo architecture / agent plan (not the public site) |
| `docs/quality/` | Milestone audits |
| `docs/governance/` | Process locks (this file, rulesets) |

Cursor rule: [`.cursor/rules/kona-docs.mdc`](../../.cursor/rules/kona-docs.mdc) (globs `apps/docs/**`).

## Voice and depth (locked)

1. **Audience.** Readers know programming and common web ideas (HTTP methods, JSON, packages, classes). They do **not** necessarily know any particular web framework’s jargon. Define every Kona concept (application, provider, container, middleware, grind, …) in plain language the first time it appears. Walk through *why* it exists and *when* it runs.

2. **Independence.** Inspiration from other ecosystems may get a **single short nod** in the introduction. After that, public docs must **not** compare Kona to other frameworks, list “equivalents,” or explain features as “like X.” Kona is documented as its own product: Kona names, Kona APIs, Kona behavior.

3. **Detail.** Prefer thorough guides over thin stubs. Include lifecycle notes, defaults, error behavior, and Kotlin **plus** Java examples for public surfaces.

4. **Internal vs public.** Component maps that exist for agents under `docs/architecture/` stay out of the public site unless rewritten in Kona-only language.

## Concurrent with milestones

Public guides for a milestone’s API surface are written **in the same wave** as the code. Do not defer docs.

- Expand `apps/docs/sidebar.mjs` and `src/content/docs/` when new APIs land.
- Kotlin and Java examples on public API pages.
- `pnpm docs:dev` / `pnpm docs:build` from the repo root.

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
