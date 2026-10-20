package org.konaframework.cli

import java.nio.file.Files
import java.nio.file.Path

/**
 * Real CSS + Inertia adapter files for `kona new`. Composes on top of the kit overlay so every
 * menu item writes a working pipeline (Vite + Tailwind / Bootstrap / custom), not a stub.
 */
internal object ScaffoldFrontends {
    fun apply(target: Path, kit: String, css: String, frontend: String) {
        if (kit == "api") {
            return
        }
        if (kit == "inertia") {
            val adapter = if (frontend.isEmpty()) "vue" else frontend
            writeInertia(target, adapter, css)
        } else if (css != "custom") {
            writeViteCss(target, css)
            writeCremaViews(target, css, brew = kit == "brew")
        }
    }

    fun writeInertia(target: Path, frontend: String, css: String) {
        val adapterPlugin = when (frontend) {
            "react" -> AdapterPlugin(
                importLine = "import react from '@vitejs/plugin-react';",
                call = "react()",
                entry = "src/main/resources/js/app.jsx",
            )
            "svelte" -> AdapterPlugin(
                importLine = "import { svelte } from '@sveltejs/vite-plugin-svelte';",
                call = "svelte()",
                entry = "src/main/resources/js/app.js",
            )
            else -> AdapterPlugin(
                importLine = "import vue from '@vitejs/plugin-vue';",
                call = "vue()",
                entry = "src/main/resources/js/app.js",
            )
        }
        writePackageJson(target, frontend, css)
        writeViteConfig(target, css, listOf(adapterPlugin))
        writeCssFile(target, css)
        when (frontend) {
            "react" -> {
                put(target, adapterPlugin.entry, reactAppJsx(css))
                put(target, "src/main/resources/js/Pages/Welcome.jsx", reactWelcome(css))
            }
            "svelte" -> {
                put(target, adapterPlugin.entry, svelteAppJs(css))
                put(target, "src/main/resources/js/Pages/Welcome.svelte", svelteWelcome(css))
            }
            else -> {
                put(target, adapterPlugin.entry, vueAppJs(css))
                put(target, "src/main/resources/js/Pages/Welcome.vue", vueWelcome(css))
            }
        }
        if (css != "custom") {
            writeCremaViews(target, css, brew = false)
        }
    }

    fun writeViteCss(target: Path, css: String) {
        writePackageJson(target, frontend = "", css = css)
        writeViteConfig(target, css, emptyList())
        writeCssFile(target, css)
        put(target, "src/main/resources/js/app.js", "import '../css/app.css';\n")
    }

    fun writeCremaViews(target: Path, css: String, brew: Boolean) {
        put(target, "src/main/resources/views/welcome.crema.html", cremaWelcome(css, brew))
        put(target, "src/main/resources/views/login.crema.html", cremaLogin(css))
        put(target, "src/main/resources/views/dashboard.crema.html", cremaDashboard(css))
    }

    fun viteEntry(frontend: String): String =
        if (frontend == "react") "src/main/resources/js/app.jsx" else "src/main/resources/js/app.js"

    private fun writePackageJson(target: Path, frontend: String, css: String) {
        put(target, "package.json", packageJson(frontend, css))
    }

    private fun writeViteConfig(target: Path, css: String, adapters: List<AdapterPlugin>) {
        val pluginImports = buildList {
            adapters.forEach { add(it.importLine) }
            if (css == "tailwind") add("import tailwindcss from '@tailwindcss/vite';")
        }
        val pluginCalls = buildList {
            adapters.forEach { add(it.call) }
            if (css == "tailwind") add("tailwindcss()")
        }
        val entry = if (adapters.isEmpty()) {
            "src/main/resources/js/app.js"
        } else {
            adapters[0].entry
        }
        val importBlock = if (pluginImports.isEmpty()) "" else pluginImports.joinToString("\n") + "\n"
        val pluginsJs = pluginCalls.joinToString(", ")
        put(
            target,
            "vite.config.js",
            """
            import { defineConfig } from 'vite';
            import { resolve } from 'node:path';
            $importBlock
            export default defineConfig({
              plugins: [$pluginsJs],
              publicDir: false,
              build: {
                outDir: 'public/build',
                emptyOutDir: true,
                manifest: true,
                rollupOptions: {
                  input: resolve('$entry'),
                },
              },
              server: {
                origin: 'http://127.0.0.1:5173',
              },
            });
            """.trimIndent() + "\n",
        )
    }

    private fun writeCssFile(target: Path, css: String) {
        val body = when (css) {
            "tailwind" ->
                """
                @import "tailwindcss";

                @theme {
                  --color-caramel: #df8723;
                  --color-accent: #74453f;
                  --color-latte: #c9a57b;
                  --color-cream: #e8d4b8;
                  --color-ink: #2a1a12;
                  --color-foam: #faf6f0;
                }
                """.trimIndent() + "\n"
            "bootstrap" ->
                """
                @import "bootstrap/dist/css/bootstrap.min.css";

                :root {
                  --bs-primary: #df8723;
                  --bs-primary-rgb: 223, 135, 35;
                  --kona-accent: #74453f;
                  --kona-foam: #faf6f0;
                }
                body { background: var(--kona-foam); color: #2a1a12; }
                .btn-primary { --bs-btn-bg: #df8723; --bs-btn-border-color: #df8723; }
                """.trimIndent() + "\n"
            else -> "body { font-family: system-ui, sans-serif; margin: 2rem; }\n"
        }
        put(target, "src/main/resources/css/app.css", body)
    }

    private fun packageJson(frontend: String, css: String): String {
        val dependencies = linkedMapOf<String, String>()
        val dev = linkedMapOf("vite" to "^6.0.0")
        when (frontend) {
            "vue" -> {
                dependencies["@inertiajs/vue3"] = "^2.0.0"
                dependencies["vue"] = "^3.5.0"
                dev["@vitejs/plugin-vue"] = "^5.2.0"
            }
            "react" -> {
                dependencies["@inertiajs/react"] = "^2.0.0"
                dependencies["react"] = "^19.0.0"
                dependencies["react-dom"] = "^19.0.0"
                dev["@vitejs/plugin-react"] = "^4.3.0"
            }
            "svelte" -> {
                dependencies["@inertiajs/svelte"] = "^2.0.0"
                dev["@sveltejs/vite-plugin-svelte"] = "^5.0.0"
                dev["svelte"] = "^5.0.0"
            }
        }
        when (css) {
            "tailwind" -> {
                dev["tailwindcss"] = "^4.1.0"
                dev["@tailwindcss/vite"] = "^4.1.0"
            }
            "bootstrap" -> dependencies["bootstrap"] = "^5.3.3"
        }
        fun mapJson(map: Map<String, String>): String {
            if (map.isEmpty()) return "{}"
            return map.entries.joinToString(",\n    ", "{\n    ", "\n  }") { (k, v) -> "\"$k\": \"$v\"" }
        }
        return """
            {
              "private": true,
              "type": "module",
              "scripts": {
                "dev": "vite",
                "build": "vite build"
              },
              "dependencies": ${mapJson(dependencies)},
              "devDependencies": ${mapJson(dev)}
            }
        """.trimIndent() + "\n"
    }

    private fun vueAppJs(css: String): String {
        val cssLine = if (css == "custom") "" else "import '../css/app.css';\n"
        return cssLine + """
            import { createApp, h } from 'vue';
            import { createInertiaApp } from '@inertiajs/vue3';

            createInertiaApp({
              resolve: (name) => {
                const pages = import.meta.glob('./Pages/**/*.vue', { eager: true });
                const page = pages[`./Pages/${'$'}{name}.vue`];
                if (!page) {
                  throw new Error(`Inertia page not found: Pages/${'$'}{name}.vue`);
                }
                return page;
              },
              setup({ el, App, props, plugin }) {
                createApp({ render: () => h(App, props) }).use(plugin).mount(el);
              },
            });
        """.trimIndent() + "\n"
    }

    private fun reactAppJsx(css: String): String {
        val cssLine = if (css == "custom") "" else "import '../css/app.css';\n"
        return cssLine + """
            import { createInertiaApp } from '@inertiajs/react';
            import { createRoot } from 'react-dom/client';

            createInertiaApp({
              resolve: (name) => {
                const pages = import.meta.glob('./Pages/**/*.jsx', { eager: true });
                const page = pages[`./Pages/${'$'}{name}.jsx`];
                if (!page) {
                  throw new Error(`Inertia page not found: Pages/${'$'}{name}.jsx`);
                }
                return page;
              },
              setup({ el, App, props }) {
                createRoot(el).render(<App {...props} />);
              },
            });
        """.trimIndent() + "\n"
    }

    private fun svelteAppJs(css: String): String {
        val cssLine = if (css == "custom") "" else "import '../css/app.css';\n"
        return cssLine + """
            import { createInertiaApp } from '@inertiajs/svelte';
            import { mount } from 'svelte';

            createInertiaApp({
              resolve: (name) => {
                const pages = import.meta.glob('./Pages/**/*.svelte', { eager: true });
                const page = pages[`./Pages/${'$'}{name}.svelte`];
                if (!page) {
                  throw new Error(`Inertia page not found: Pages/${'$'}{name}.svelte`);
                }
                return page;
              },
              setup({ el, App, props }) {
                mount(App, { target: el, props });
              },
            });
        """.trimIndent() + "\n"
    }

    private fun vueWelcome(css: String): String =
        when (css) {
            "tailwind" ->
                """
                <script setup>
                defineProps({
                  title: { type: String, default: 'Welcome' },
                  message: { type: String, default: '' },
                });
                </script>

                <template>
                  <main class="min-h-screen bg-foam text-ink p-8 max-w-xl">
                    <h1 class="text-3xl font-bold text-accent">{{ title }}</h1>
                    <p v-if="message" class="mt-4 opacity-90">{{ message }}</p>
                    <p class="mt-8 text-sm">
                      <a class="text-caramel" href="https://docs.konaframework.org/">docs.konaframework.org</a>
                    </p>
                  </main>
                </template>
                """.trimIndent() + "\n"
            "bootstrap" ->
                """
                <script setup>
                defineProps({
                  title: { type: String, default: 'Welcome' },
                  message: { type: String, default: '' },
                });
                </script>

                <template>
                  <main class="container py-5">
                    <h1 class="display-6">{{ title }}</h1>
                    <p v-if="message" class="lead">{{ message }}</p>
                    <p class="mt-4">
                      <a href="https://docs.konaframework.org/">docs.konaframework.org</a>
                    </p>
                  </main>
                </template>
                """.trimIndent() + "\n"
            else ->
                """
                <script setup>
                defineProps({
                  title: { type: String, default: 'Welcome' },
                  message: { type: String, default: '' },
                });
                </script>

                <template>
                  <main style="font-family: system-ui, sans-serif; padding: 2rem; max-width: 40rem;">
                    <h1>{{ title }}</h1>
                    <p v-if="message">{{ message }}</p>
                    <p><a href="https://docs.konaframework.org/">docs.konaframework.org</a></p>
                  </main>
                </template>
                """.trimIndent() + "\n"
        }

    private fun reactWelcome(css: String): String =
        when (css) {
            "tailwind" ->
                """
                export default function Welcome({ title = 'Welcome', message = '' }) {
                  return (
                    <main className="min-h-screen bg-foam text-ink p-8 max-w-xl">
                      <h1 className="text-3xl font-bold text-accent">{title}</h1>
                      {message ? <p className="mt-4 opacity-90">{message}</p> : null}
                      <p className="mt-8 text-sm">
                        <a className="text-caramel" href="https://docs.konaframework.org/">docs.konaframework.org</a>
                      </p>
                    </main>
                  );
                }
                """.trimIndent() + "\n"
            "bootstrap" ->
                """
                export default function Welcome({ title = 'Welcome', message = '' }) {
                  return (
                    <main className="container py-5">
                      <h1 className="display-6">{title}</h1>
                      {message ? <p className="lead">{message}</p> : null}
                      <p className="mt-4">
                        <a href="https://docs.konaframework.org/">docs.konaframework.org</a>
                      </p>
                    </main>
                  );
                }
                """.trimIndent() + "\n"
            else ->
                """
                export default function Welcome({ title = 'Welcome', message = '' }) {
                  return (
                    <main style={{ fontFamily: 'system-ui, sans-serif', padding: '2rem', maxWidth: '40rem' }}>
                      <h1>{title}</h1>
                      {message ? <p>{message}</p> : null}
                      <p><a href="https://docs.konaframework.org/">docs.konaframework.org</a></p>
                    </main>
                  );
                }
                """.trimIndent() + "\n"
        }

    private fun svelteWelcome(css: String): String =
        when (css) {
            "tailwind" ->
                """
                <script>
                  let { title = 'Welcome', message = '' } = ${'$'}props();
                </script>

                <main class="min-h-screen bg-foam text-ink p-8 max-w-xl">
                  <h1 class="text-3xl font-bold text-accent">{title}</h1>
                  {#if message}
                    <p class="mt-4 opacity-90">{message}</p>
                  {/if}
                  <p class="mt-8 text-sm">
                    <a class="text-caramel" href="https://docs.konaframework.org/">docs.konaframework.org</a>
                  </p>
                </main>
                """.trimIndent() + "\n"
            "bootstrap" ->
                """
                <script>
                  let { title = 'Welcome', message = '' } = ${'$'}props();
                </script>

                <main class="container py-5">
                  <h1 class="display-6">{title}</h1>
                  {#if message}
                    <p class="lead">{message}</p>
                  {/if}
                  <p class="mt-4">
                    <a href="https://docs.konaframework.org/">docs.konaframework.org</a>
                  </p>
                </main>
                """.trimIndent() + "\n"
            else ->
                """
                <script>
                  let { title = 'Welcome', message = '' } = ${'$'}props();
                </script>

                <main style="font-family: system-ui, sans-serif; padding: 2rem; max-width: 40rem;">
                  <h1>{title}</h1>
                  {#if message}
                    <p>{message}</p>
                  {/if}
                  <p><a href="https://docs.konaframework.org/">docs.konaframework.org</a></p>
                </main>
                """.trimIndent() + "\n"
        }

    private val viteTag = "@vite('src/main/resources/js/app.js')"

    private fun cremaWelcome(css: String, brew: Boolean): String {
        val title = if (brew) "Your Brew app is ready." else "Your app is ready."
        val lede = if (brew) {
            "Crema views, Cadence models, session auth, and Brew reactive components on one page."
        } else {
            "A batteries-included JVM web app with Crema views, Cadence models, and session auth."
        }
        val brewBlock = if (!brew) {
            ""
        } else {
            """
            <section class="brew-card card mb-4 p-4 border rounded">
              <h2>Brew sample — <code>app.brew.Counter</code></h2>
              <brew:counter :start="0" />
              <p class="hint text-muted mt-3">
                Edit <code>app/brew/Counter.kt</code> (or <code>.java</code>), or scaffold more with
                <code>./grind make:brew Todos --class</code> and embed <code>&lt;brew:todos /&gt;</code>.
              </p>
            </section>
            """.trimIndent()
        }
        val docsHref = if (brew) "https://docs.konaframework.org/docs/brew/" else "https://docs.konaframework.org/"
        val docsLabel = if (brew) "Brew docs" else "Docs"
        return when (css) {
            "tailwind" ->
                """
                <!DOCTYPE html>
                <html lang="en">
                  <head>
                    <meta charset="utf-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1" />
                    <title>{{ name }}</title>
                    $viteTag
                  </head>
                  <body class="min-h-screen bg-foam text-ink">
                    <div class="max-w-xl mx-auto px-6 py-14">
                      <div class="flex items-center gap-3 mb-10">
                        <img src="/branding/kona-logo.svg" alt="Kona" width="52" height="52" />
                        <span class="font-bold tracking-wide text-accent">{{ name }}</span>
                      </div>
                      <h1 class="text-4xl font-bold text-accent mb-4">$title</h1>
                      <p class="text-lg opacity-90 mb-8">$lede</p>
                      <div class="flex flex-wrap gap-3 mb-10">
                        <a class="px-4 py-3 rounded-lg bg-caramel text-white font-semibold" href="/login">Sign in</a>
                        <a class="px-4 py-3 rounded-lg border border-latte text-accent font-semibold" href="/dashboard">Dashboard</a>
                        <a class="px-4 py-3 rounded-lg border border-latte text-accent font-semibold" href="$docsHref">$docsLabel</a>
                      </div>
                      $brewBlock
                      <div class="p-4 rounded-xl bg-white/60 border border-latte text-sm">
                        <p>Demo user after <code class="text-caramel">./grind migrate</code> and <code class="text-caramel">./grind db:seed</code>:</p>
                        <p><code class="text-caramel">demo@kona.test</code> / <code class="text-caramel">password</code></p>
                        <p>JSON users API: <code class="text-caramel">GET /api/users</code></p>
                      </div>
                      <footer class="mt-10 text-sm text-accent/70">
                        Built with <a class="text-caramel" href="https://docs.konaframework.org/">Kona</a>
                      </footer>
                    </div>
                  </body>
                </html>
                """.trimIndent() + "\n"
            else ->
                """
                <!DOCTYPE html>
                <html lang="en">
                  <head>
                    <meta charset="utf-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1" />
                    <title>{{ name }}</title>
                    $viteTag
                  </head>
                  <body>
                    <div class="container py-5" style="max-width: 40rem;">
                      <div class="d-flex align-items-center gap-3 mb-4">
                        <img src="/branding/kona-logo.svg" alt="Kona" width="52" height="52" />
                        <strong>{{ name }}</strong>
                      </div>
                      <h1 class="display-6">$title</h1>
                      <p class="lead">$lede</p>
                      <div class="d-flex flex-wrap gap-2 mb-4">
                        <a class="btn btn-primary" href="/login">Sign in</a>
                        <a class="btn btn-outline-secondary" href="/dashboard">Dashboard</a>
                        <a class="btn btn-outline-secondary" href="$docsHref">$docsLabel</a>
                      </div>
                      $brewBlock
                      <div class="card">
                        <div class="card-body">
                          <p>Demo user after <code>./grind migrate</code> and <code>./grind db:seed</code>:</p>
                          <p><code>demo@kona.test</code> / <code>password</code></p>
                          <p>JSON users API: <code>GET /api/users</code></p>
                        </div>
                      </div>
                      <p class="mt-4 text-muted">
                        Built with <a href="https://docs.konaframework.org/">Kona</a>
                      </p>
                    </div>
                  </body>
                </html>
                """.trimIndent() + "\n"
        }
    }

    private fun cremaLogin(css: String): String =
        when (css) {
            "tailwind" ->
                """
                <!DOCTYPE html>
                <html lang="en">
                  <head>
                    <meta charset="utf-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1" />
                    <title>Sign in · {{ name }}</title>
                    $viteTag
                  </head>
                  <body class="min-h-screen bg-foam text-ink">
                    <div class="max-w-sm mx-auto px-5 py-14">
                      <div class="flex items-center gap-3 mb-8">
                        <img src="/branding/kona-logo.svg" alt="Kona" width="44" height="44" />
                        <strong>{{ name }}</strong>
                      </div>
                      <h1 class="text-2xl font-bold text-accent mb-1">Sign in</h1>
                      <p class="opacity-80 mb-6">Use the demo account from the welcome page, or your own seeded user.</p>
                      @if(error)
                        <div class="bg-red-100 text-red-800 p-3 rounded-lg mb-4">{{ error }}</div>
                      @endif
                      <form method="post" action="/login">
                        @csrf
                        <label class="block text-sm font-semibold mb-1" for="email">Email</label>
                        <input class="w-full mb-4 p-2 border border-latte rounded-lg" id="email" name="email" type="email" value="{{ email }}" required autocomplete="username" />
                        <label class="block text-sm font-semibold mb-1" for="password">Password</label>
                        <input class="w-full mb-4 p-2 border border-latte rounded-lg" id="password" name="password" type="password" required autocomplete="current-password" />
                        <button class="w-full py-3 rounded-lg bg-caramel text-white font-bold" type="submit">Sign in</button>
                      </form>
                      <a class="inline-block mt-5 text-caramel" href="/">← Back home</a>
                    </div>
                  </body>
                </html>
                """.trimIndent() + "\n"
            else ->
                """
                <!DOCTYPE html>
                <html lang="en">
                  <head>
                    <meta charset="utf-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1" />
                    <title>Sign in · {{ name }}</title>
                    $viteTag
                  </head>
                  <body>
                    <div class="container py-5" style="max-width: 24rem;">
                      <div class="d-flex align-items-center gap-2 mb-4">
                        <img src="/branding/kona-logo.svg" alt="Kona" width="44" height="44" />
                        <strong>{{ name }}</strong>
                      </div>
                      <h1 class="h3">Sign in</h1>
                      <p class="text-muted">Use the demo account from the welcome page, or your own seeded user.</p>
                      @if(error)
                        <div class="alert alert-danger">{{ error }}</div>
                      @endif
                      <form method="post" action="/login">
                        @csrf
                        <div class="mb-3">
                          <label class="form-label" for="email">Email</label>
                          <input class="form-control" id="email" name="email" type="email" value="{{ email }}" required autocomplete="username" />
                        </div>
                        <div class="mb-3">
                          <label class="form-label" for="password">Password</label>
                          <input class="form-control" id="password" name="password" type="password" required autocomplete="current-password" />
                        </div>
                        <button class="btn btn-primary w-100" type="submit">Sign in</button>
                      </form>
                      <a class="d-inline-block mt-3" href="/">← Back home</a>
                    </div>
                  </body>
                </html>
                """.trimIndent() + "\n"
        }

    private fun cremaDashboard(css: String): String =
        when (css) {
            "tailwind" ->
                """
                <!DOCTYPE html>
                <html lang="en">
                  <head>
                    <meta charset="utf-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1" />
                    <title>Dashboard · {{ name }}</title>
                    $viteTag
                  </head>
                  <body class="min-h-screen bg-foam text-ink">
                    <header class="flex items-center justify-between gap-4 px-6 py-4 border-b border-latte bg-white/60">
                      <a class="flex items-center gap-2 font-bold text-accent" href="/">
                        <img src="/branding/kona-logo.svg" alt="" width="36" height="36" />
                        {{ name }}
                      </a>
                      <a class="text-caramel" href="https://docs.konaframework.org/">Docs</a>
                    </header>
                    <main class="max-w-xl mx-auto px-6 py-10">
                      <h1 class="text-3xl font-bold text-accent mb-4">Dashboard</h1>
                      <div class="p-4 rounded-xl bg-white border border-latte mb-4">
                        <p>Signed in as <strong>{{ userName }}</strong></p>
                        <p>{{ userEmail }}</p>
                      </div>
                      <form method="post" action="/logout">
                        @csrf
                        <button class="px-4 py-2 rounded-lg bg-accent text-white font-semibold" type="submit">Sign out</button>
                      </form>
                    </main>
                  </body>
                </html>
                """.trimIndent() + "\n"
            else ->
                """
                <!DOCTYPE html>
                <html lang="en">
                  <head>
                    <meta charset="utf-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1" />
                    <title>Dashboard · {{ name }}</title>
                    $viteTag
                  </head>
                  <body>
                    <header class="d-flex align-items-center justify-content-between px-4 py-3 border-bottom bg-white">
                      <a class="d-flex align-items-center gap-2 text-decoration-none fw-bold" href="/">
                        <img src="/branding/kona-logo.svg" alt="" width="36" height="36" />
                        {{ name }}
                      </a>
                      <a href="https://docs.konaframework.org/">Docs</a>
                    </header>
                    <main class="container py-5" style="max-width: 40rem;">
                      <h1 class="h3">Dashboard</h1>
                      <div class="card mb-3">
                        <div class="card-body">
                          <p>Signed in as <strong>{{ userName }}</strong></p>
                          <p>{{ userEmail }}</p>
                        </div>
                      </div>
                      <form method="post" action="/logout">
                        @csrf
                        <button class="btn btn-dark" type="submit">Sign out</button>
                      </form>
                    </main>
                  </body>
                </html>
                """.trimIndent() + "\n"
        }

    private fun put(target: Path, relative: String, content: String) {
        val path = target.resolve(relative)
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
    }

    data class AdapterPlugin(val importLine: String, val call: String, val entry: String)
}
