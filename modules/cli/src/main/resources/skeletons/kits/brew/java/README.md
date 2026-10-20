# {{appName}}

Kona application (Java) — **Brew** starter kit.

```bash
./grind migrate
./grind db:seed
./grind serve
```

Includes `org.konaframework:kona-brew` and `kona-crema`, the branded landing / login / dashboard
(Tailwind or Bootstrap via `kona new --css=`), and a sample `app.brew.Counter` mounted on welcome
as `<brew:counter />`.

## Adding another Brew component

```bash
./grind make:brew Hello --lang=java
```

That writes `app/brew/Hello.java`. Classes under `app.brew` resolve automatically — no manual
registration. Embed in a Crema view:

```crema
<brew:hello />
{{-- or --}}
@brew('hello')
```

See https://docs.konaframework.org/docs/brew/
