# {{appName}}

Kona application (Kotlin) — **Inertia + Vue** starter kit with full auth scaffolding.

```bash
./grind migrate
./grind db:seed
npm install
npm run dev
./grind serve
```

## What you get

- Inertia Vue pages: login, register (toggleable), forgot / reset password, email verification, confirm password, dashboard
- Settings: profile, password, two-factor authentication (TOTP + recovery codes), appearance (light / dark / system)
- Shared Inertia props: `auth.user`, `flash`, `errors`, `name`, `features`
- `kona-auth` two-factor: enrollment, confirmation, login challenge, rate limiting

Demo user after seed: `demo@kona.test` / `password`.

## Feature flags (`.env`)

```env
AUTH_REGISTRATION=true
AUTH_EMAIL_VERIFICATION=true
AUTH_TWO_FACTOR=true
```

## Notes

Tailwind CSS is the default for this kit. Use `--css=bootstrap` or `--css=custom` if you prefer. Add `--typescript` / `--ts` for a TypeScript entry and typed Welcome page; auth Vue SFCs stay `<script setup>` and work with either entry.
