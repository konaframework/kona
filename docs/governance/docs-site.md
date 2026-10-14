# Public documentation site

Stack (same as Shamar): **Astro + Starlight + `@almasix/starlight-theme`**, pnpm workspace, Cloudflare Workers static assets (`wrangler`).

| Path | Role |
|------|------|
| `apps/docs/` | Starlight site → [konaframework.dev](https://konaframework.dev) |
| `docs/architecture/` | In-repo architecture / agent plan (not the public site) |
| `docs/quality/` | Milestone audits |
| `docs/governance/` | Process locks (this file, rulesets) |

## Concurrent with milestones

Public guides for a milestone’s parity slice are written **in the same wave** as the code (same PR or a docs PR opened before / beside the milestone PR). Do not defer docs to “after M5”.

- Expand `apps/docs/sidebar.mjs` and `src/content/docs/` when M2+ APIs land.
- Keep Kotlin **and** Java examples on public API pages (Law 8).
- `pnpm docs:dev` / `pnpm docs:build` from the repo root.

## Deploy

```bash
pnpm pages:build
pnpm --filter @kona/docs cf:deploy
```

Production site URL: `PUBLIC_SITE_URL=https://konaframework.dev`.
