package dev.kona.crema

/**
 * Thin Vite tag helper for Crema templates — no Inertia dependency.
 * Emits script/link tags pointing at `/build/...` paths.
 */
object ViteTags {
    @JvmStatic
    fun tags(vararg entries: String): String =
        entries.joinToString("\n") { tag(it) }

    @JvmStatic
    fun tags(entries: Collection<String>): String = tags(*entries.toTypedArray())

    @JvmStatic
    fun tag(entry: String): String {
        val trimmed = entry.trim().removePrefix("/")
        val href =
            if (trimmed.startsWith("build/")) {
                "/$trimmed"
            } else {
                "/build/$trimmed"
            }
        return if (trimmed.endsWith(".css")) {
            """<link rel="stylesheet" href="$href">"""
        } else {
            """<script type="module" src="$href"></script>"""
        }
    }
}
