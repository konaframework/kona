# Kona

**Kona** is a full-stack, developer-friendly web framework for the JVM (Kotlin-first, first-class Java support), inspired by Laravel and Almasix.

- **Site (planned):** [konaframework.dev](https://konaframework.dev)
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

Milestone **M0** locks architecture and working rules. Framework code begins in **M1**.

| Doc | Purpose |
|-----|---------|
| [AGENTS.md](AGENTS.md) | Rules every agent must follow |
| [MILESTONES.md](MILESTONES.md) | Ordered milestones and parity checklists |
| [docs/architecture/PLAN.md](docs/architecture/PLAN.md) | Full architectural plan |
| [docs/architecture/components.md](docs/architecture/components.md) | Component map and conventions |
| [docs/governance/github-rulesets.md](docs/governance/github-rulesets.md) | Never mutate GitHub rulesets |

## License

Apache License 2.0 — see [LICENSE](LICENSE).
