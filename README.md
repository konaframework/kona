# Kona

**Kona** is a full-stack, developer-friendly web framework for the JVM (Kotlin-first, first-class Java support), inspired by Laravel and Almasix.

- **Site:** [konaframework.dev](https://konaframework.dev) (Astro + Starlight + `@almasix/starlight-theme` in `apps/docs/`)
- **Maven group:** `dev.kona`
- **Org:** [konaframework](https://github.com/konaframework)

## Named components

| Kona | Laravel / Almasix | Role |
|------|-------------------|------|
| **Kona** | Laravel / Almasix core | Framework core, container, routing, boot |
| **grind** | Artisan / `smith` | CLI |
| **steam** | Tinker | REPL (`grind steam`) |
| **Extract** | Eloquent | ActiveRecord ORM |
| **Crema** | Blade / Prism | Templates |
| **drip** | HTTP client | Fluent client/SDK |

```text
./grind | ./grind steam | Extract | *.crema.html | Kona.start(args)
```

## Status

**M0–M1** on `main`. Active coding milestone: **M2 Extract**. Public docs ship concurrently from `apps/docs/`.

```bash
pnpm install
pnpm docs:dev    # http://localhost:4321
pnpm docs:build
```

| Doc | Purpose |
|-----|---------|
| [AGENTS.md](AGENTS.md) | Rules every agent must follow |
| [MILESTONES.md](MILESTONES.md) | Ordered milestones and parity checklists |
| [docs/architecture/PLAN.md](docs/architecture/PLAN.md) | Full architectural plan |
| [docs/architecture/components.md](docs/architecture/components.md) | Component map and conventions |
| [docs/governance/docs-site.md](docs/governance/docs-site.md) | Public docs stack (Shamar-compatible) |
| [docs/governance/github-rulesets.md](docs/governance/github-rulesets.md) | Never mutate GitHub rulesets |

## License

Apache License 2.0 — see [LICENSE](LICENSE).
