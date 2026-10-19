# {{appName}}

Kona application (Java) — **Brew** starter kit.

```bash
./grind migrate
./grind db:seed
./grind serve
```

Includes `org.konaframework:kona-brew` and `kona-crema`, plus the same branded landing, session login, dashboard, and `User` model as the Crema kit.

## Adding a Brew component

Create a class under `app.brew` that extends `org.konaframework.brew.Component`, implement `template()`, register it with `Brew.register(Hello.class)` in `bootstrap.App`, then embed it in a Crema view (`<brew:hello />` or `wire:name="hello"`).
