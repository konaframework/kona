package org.konaframework.lsp

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
            "hasSection",
            "sectionMissing",
            "stack",
            "push",
            "endpush",
            "prepend",
            "endprepend",
            "include",
            "includeIf",
            "includeWhen",
            "includeUnless",
            "includeFirst",
            "if",
            "elseif",
            "else",
            "endif",
            "unless",
            "endunless",
            "isset",
            "endisset",
            "empty",
            "endempty",
            "while",
            "endwhile",
            "foreach",
            "endforeach",
            "forelse",
            "endforelse",
            "each",
            "endeach",
            "for",
            "endfor",
            "switch",
            "case",
            "break",
            "default",
            "endswitch",
            "auth",
            "endauth",
            "guest",
            "endguest",
            "can",
            "endcan",
            "cannot",
            "endcannot",
            "error",
            "enderror",
            "once",
            "endonce",
            "verbatim",
            "endverbatim",
            "component",
            "endcomponent",
            "slot",
            "endslot",
            "json",
            "csrf",
            "method",
            "asset",
            "lang",
            "vite",
        )

    /** Completion labels including the leading `@`. */
    @JvmField
    val COMPLETION_LABELS: List<String> = KNOWN.sorted().map { "@$it" }

    @JvmStatic
    fun isKnown(name: String): Boolean = name in KNOWN
}
