package dev.kona.grind

/**
 * `grind assets:vite` — prints Vite integration setup reminders (no process spawn).
 */
class AssetsViteCommand {
    fun run(args: List<String>): Int {
        val (_, options) = parseOptions(args)
        val io = GrindIO.fromOptions(options)
        io.box(
            "Vite asset bundling",
            listOf(
                "1. Add package.json + vite.config.js (Inertia kit includes stubs).",
                "2. Dev:  npm run dev   → writes public/hot with the Vite URL",
                "3. Prod: npm run build → writes public/build/manifest.json",
                "4. In HTML / Crema: Vite.tags(\"…\") or Inertia {!! vite !!}",
                "Docs: /docs/assets/vite",
            ),
        )
        return 0
    }
}
