# Kona

**Kona** is a full-stack, developer-friendly web framework for the JVM (Kotlin-first, first-class Java support), inspired by Laravel and Almasix.

- **Site:** [konaframework.dev](https://konaframework.dev) · Pages: [konaframework.github.io/kona](https://konaframework.github.io/kona/) (Astro + Starlight + `@almasix/starlight-theme` in `apps/docs/`)
- **Maven group:** `io.konaframework`
- **Org:** [konaframework](https://github.com/konaframework)

## Named components

| Kona | Laravel / Almasix | Role |
|------|-------------------|------|
| **Kona** | Laravel / Almasix core | Framework core, container, routing, boot |
| **kona** (CLI) | `laravel` new | Global: create apps |
| **grind** | Artisan / `smith` | App-local CLI (`./grind`) |
| **steam** | Tinker | REPL (`grind steam`) |
| **Cadence** | Eloquent | ActiveRecord ORM |
| **Crema** | Blade / Prism | Templates |
| **drip** | HTTP client | Fluent client/SDK |

```text
kona new my-app | cd my-app && ./grind | ./grind steam | Cadence | *.crema.html
```

```bash
curl -fsSL https://raw.githubusercontent.com/konaframework/kona/main/scripts/install.sh | bash
kona new my-app
cd my-app && ./grind serve  # ./grind is created by kona new — not present under samples/
```

Repo samples (`samples/hello-*`) are monorepo modules: run `./gradlew :hello-kotlin:run` / `:hello-java:run` from this root.

## Status

**M0–M1** on `main`. Active coding milestone: **M2 Cadence**. Public docs ship concurrently from `apps/docs/`.

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
