package dev.kona.lsp

/**
 * Built-in Crema directive names recognized by the language server and grammar docs.
 */
object CremaDirectives {
    @JvmField
    val KNOWN: Set<String> =
        setOf(
            "extends",
            "section",
            "endsection",
            "show",
            "yield",
            "parent",
            "stack",
            "push",
            "endpush",
            "include",
            "includeIf",
            "includeWhen",
            "if",
            "else",
            "endif",
            "foreach",
            "endforeach",
            "slot",
            "endslot",
            "json",
            "csrf",
        )

    /** Completion labels including the leading `@`. */
    @JvmField
    val COMPLETION_LABELS: List<String> = KNOWN.sorted().map { "@$it" }

    @JvmStatic
    fun isKnown(name: String): Boolean = name in KNOWN
}
