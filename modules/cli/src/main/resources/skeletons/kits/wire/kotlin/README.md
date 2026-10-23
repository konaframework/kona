# {{appName}}

Kona application (Kotlin) — **Brew** starter kit.

```bash
./grind migrate
./grind db:seed
./grind serve
```

Includes `org.konaframework:kona-wire` and `kona-crema`, the branded landing / login / dashboard
(Tailwind or Bootstrap via `kona new --css=`), and a sample `app.wire.Counter` mounted on welcome
as `<wire:counter />`.

## Adding another Brew component

```bash
./grind make:wire Hello
```

That writes `app/wire/Hello.kt`. Classes under `app.wire` resolve automatically — no manual
registration. Embed in a Crema view:

```crema
<wire:hello />
{{-- or --}}
@wire('hello')
```

See https://docs.konaframework.org/docs/wire/
