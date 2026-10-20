# {{appName}}

Kona application (Kotlin) — **Inertia** starter kit.

```bash
./grind migrate
./grind db:seed
npm install
npm run dev
./grind serve
```

Includes `org.konaframework:kona-inertia` + `kona-crema` from Maven Central, a Vite app (`--frontend=vue|react|svelte`, `--css=tailwind|bootstrap|custom`), and `HandleInertia` middleware.

Landing, login, and dashboard ship as **Crema HTML** in this starter so session auth works before you add Inertia pages. Use `./grind make:inertia` (or hand-roll React/Vue pages) as you grow; keep `/login` Crema until you replace it.
