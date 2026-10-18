package org.konaframework.grind

import java.nio.file.Files
import java.nio.file.Path

/**
 * Interactive / flagged frontend scaffold after `./grind install inertia`.
 *
 * Writes Vite + adapter stubs so the app can reach a working first page.
 * Client protocol details: https://inertiajs.com/
 */
class InertiaFrontendSetup(
    private val projectRoot: Path,
    private val io: GrindIO,
    private val prompts: Prompts,
    private val consoleAvailable: Boolean,
) {
    /** Convenience: non-interactive console detection (false). Prefer the primary ctor from grind. */
    constructor(projectRoot: Path, io: GrindIO) : this(projectRoot, io, Prompts(), false)

    enum class Stack(
        val id: String,
        val label: String,
    ) {
        VUE("vue", "Vue 3 (@inertiajs/vue3)"),
        REACT("react", "React (@inertiajs/react)"),
        SVELTE("svelte", "Svelte (@inertiajs/svelte)"),
        NONE("none", "Skip — I will wire the client myself"),
        ;

        companion object {
            fun fromId(raw: String): Stack? = entries.firstOrNull { it.id.equals(raw, ignoreCase = true) }
        }
    }

    private enum class Adapter { VUE, REACT, SVELTE }

    /**
     * @param stackFlag `--frontend=` value, or null to prompt / default
     * @param noInteractive force non-interactive (default Vue when stack omitted)
     * @return selected stack
     */
    fun run(
        stackFlag: String? = null,
        noInteractive: Boolean = false,
    ): Stack {
        val stack = resolveStack(stackFlag, noInteractive)
        if (stack == Stack.NONE) {
            io.info("Skipped frontend scaffold. See https://inertiajs.com/client-side-setup")
            printServerChecklist()
            return stack
        }
        val written = writeScaffold(stack)
        written.forEach { io.success("Wrote $it") }
        io.writeln()
        io.title("Frontend next steps")
        io.twoColumn("1.", "npm install")
        io.twoColumn("2.", "npm run dev    # Vite — leave running in another terminal")
        io.twoColumn("3.", "./grind serve  # Kona HTTP")
        if (stack == Stack.REACT) {
            io.warn(
                "React entry is src/main/resources/js/app.jsx — set " +
                    "inertia viteEntries (or Inertia.manager().viteEntries) to that path.",
            )
        }
        io.info("Client adapters & docs: https://inertiajs.com/")
        printServerChecklist()
        return stack
    }

    fun resolveStack(
        stackFlag: String?,
        noInteractive: Boolean,
    ): Stack {
        if (stackFlag != null) {
            return Stack.fromId(stackFlag)
                ?: error("Unknown --frontend=$stackFlag (use vue, react, svelte, or none)")
        }
        val interactive = !noInteractive && consoleAvailable
        if (!interactive) {
            io.info("Non-interactive install — scaffolding Vue 3 frontend (pass --frontend= to choose).")
            return Stack.VUE
        }
        val label =
            prompts.choice(
                "Which Inertia frontend stack should we scaffold?",
                Stack.entries.map { it.label },
                defaultIndex = 0,
            )
        return Stack.entries.first { it.label == label }
    }

    fun writeScaffold(stack: Stack): List<String> {
        val adapter =
            when (stack) {
                Stack.VUE -> Adapter.VUE
                Stack.REACT -> Adapter.REACT
                Stack.SVELTE -> Adapter.SVELTE
                Stack.NONE -> throw IllegalArgumentException("stack is NONE")
            }
        val written = mutableListOf<String>()
        fun put(
            relative: String,
            content: String,
        ) {
            val path = projectRoot.resolve(relative)
            Files.createDirectories(path.parent)
            Files.writeString(path, content.trimStart())
            written += relative
        }

        put("package.json", packageJson(adapter))
        put("vite.config.js", viteConfig(adapter))
        ensureRootView()?.let { written += it }
        when (adapter) {
            Adapter.VUE -> {
                put("src/main/resources/js/app.js", vueAppJs)
                put("src/main/resources/js/Pages/Welcome.vue", vueWelcome)
            }
            Adapter.REACT -> {
                put("src/main/resources/js/app.jsx", reactAppJsx)
                put("src/main/resources/js/Pages/Welcome.jsx", reactWelcome)
            }
            Adapter.SVELTE -> {
                put("src/main/resources/js/app.js", svelteAppJs)
                put("src/main/resources/js/Pages/Welcome.svelte", svelteWelcome)
            }
        }
        put("public/.gitkeep", "")
        return written
    }

    private fun ensureRootView(): String? {
        val relative = "src/main/resources/views/app.crema.html"
        val path = projectRoot.resolve(relative)
        if (Files.isRegularFile(path)) return null
        Files.createDirectories(path.parent)
        Files.writeString(path, rootViewHtml)
        return relative
    }

    private fun printServerChecklist() {
        io.writeln()
        io.title("Server checklist")
        io.info("• InertiaServiceProvider registered (grind install does this)")
        io.info("• Config: inertia.root_view=app, inertia.version=…")
        io.info("• Push HandleInertiaRequests (or kit HandleInertia) after boot")
        io.info("• Render with Inertia.render(\"Pages/Welcome\", props)")
        io.info("Full guide: https://docs.konaframework.org/docs/inertia/guides/installation/")
    }

    companion object {
        private fun packageJson(adapter: Adapter): String {
            val deps =
                when (adapter) {
                    Adapter.VUE ->
                        """
                        "dependencies": {
                          "@inertiajs/vue3": "^2.0.0",
                          "vue": "^3.5.0"
                        },
                        "devDependencies": {
                          "@vitejs/plugin-vue": "^5.2.0",
                          "vite": "^6.0.0"
                        }
                        """.trimIndent()
                    Adapter.REACT ->
                        """
                        "dependencies": {
                          "@inertiajs/react": "^2.0.0",
                          "react": "^19.0.0",
                          "react-dom": "^19.0.0"
                        },
                        "devDependencies": {
                          "@vitejs/plugin-react": "^4.3.0",
                          "vite": "^6.0.0"
                        }
                        """.trimIndent()
                    Adapter.SVELTE ->
                        """
                        "dependencies": {
                          "@inertiajs/svelte": "^2.0.0"
                        },
                        "devDependencies": {
                          "@sveltejs/vite-plugin-svelte": "^5.0.0",
                          "svelte": "^5.0.0",
                          "vite": "^6.0.0"
                        }
                        """.trimIndent()
                }
            return """
                {
                  "private": true,
                  "type": "module",
                  "scripts": {
                    "dev": "vite",
                    "build": "vite build"
                  },
                  $deps
                }
                """.trimIndent() + "\n"
        }

        private fun viteConfig(adapter: Adapter): String {
            val pluginImport =
                when (adapter) {
                    Adapter.VUE -> "import vue from '@vitejs/plugin-vue';\n"
                    Adapter.REACT -> "import react from '@vitejs/plugin-react';\n"
                    Adapter.SVELTE -> "import { svelte } from '@sveltejs/vite-plugin-svelte';\n"
                }
            val plugin =
                when (adapter) {
                    Adapter.VUE -> "vue()"
                    Adapter.REACT -> "react()"
                    Adapter.SVELTE -> "svelte()"
                }
            val entry =
                when (adapter) {
                    Adapter.REACT -> "src/main/resources/js/app.jsx"
                    Adapter.VUE, Adapter.SVELTE -> "src/main/resources/js/app.js"
                }
            return """
                import { defineConfig } from 'vite';
                import { resolve } from 'node:path';
                ${pluginImport}
                export default defineConfig({
                  plugins: [$plugin],
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
                """.trimIndent() + "\n"
        }

        private val rootViewHtml =
            """
            <!DOCTYPE html>
            <html lang="en">
              <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1" />
                <title inertia>Kona</title>
                {!! vite !!}
              </head>
              <body>
                <div id="app" data-page="{!! pageAttr !!}"></div>
              </body>
            </html>
            """.trimIndent() + "\n"

        private val vueAppJs =
            """
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

        private val vueWelcome =
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
              </main>
            </template>
            """.trimIndent() + "\n"

        private val reactAppJsx =
            """
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

        private val reactWelcome =
            """
            export default function Welcome({ title = 'Welcome', message = '' }) {
              return (
                <main style={{ fontFamily: 'system-ui, sans-serif', padding: '2rem', maxWidth: '40rem' }}>
                  <h1>{title}</h1>
                  {message ? <p>{message}</p> : null}
                </main>
              );
            }
            """.trimIndent() + "\n"

        private val svelteAppJs =
            """
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

        private val svelteWelcome =
            """
            <script>
              let { title = 'Welcome', message = '' } = ${'$'}props();
            </script>

            <main style="font-family: system-ui, sans-serif; padding: 2rem; max-width: 40rem;">
              <h1>{title}</h1>
              {#if message}
                <p>{message}</p>
              {/if}
            </main>
            """.trimIndent() + "\n"
    }
}
