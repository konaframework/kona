package org.konaframework.firefly.support

/**
 * Content-column widths for panel, page, and resource max-width settings.
 *
 * Pass a constant, or the same token as a string: `container`, `prose`, `screen-s`,
 * `screen-m`, `screen-l`, `screen-xl`, `screen-2xl`, `xs`…`7xl`, `full`, `screen`,
 * or a bracketed length such as `[900px]`.
 *
 * [ExtraSmall] through [SevenExtraLarge], [Full], [Screen], and [Prose] stay on
 * `data-ff-max-width`. `prose` already meant that attribute (65ch). [Container] and
 * the screen constants add a class (`container`, `max-w-screen-sm`, …).
 *
 * Older PascalCase strings (`Prose`, `ScreenSmall`, `ScreenExtraLarge`, `Container`,
 * and the other screen names) still resolve. Prefer the short token or the enum.
 */
enum class MaxWidth(val token: String, private val widthClass: String?) {
    ExtraSmall("xs", null),
    Small("sm", null),
    Medium("md", null),
    Large("lg", null),
    ExtraLarge("xl", null),
    TwoExtraLarge("2xl", null),
    ThreeExtraLarge("3xl", null),
    FourExtraLarge("4xl", null),
    FiveExtraLarge("5xl", null),
    SixExtraLarge("6xl", null),
    SevenExtraLarge("7xl", null),
    Full("full", null),
    Screen("screen", null),
    Prose("prose", null),
    Container("container", "container"),
    ScreenSmall("screen-s", "max-w-screen-sm"),
    ScreenMedium("screen-m", "max-w-screen-md"),
    ScreenLarge("screen-l", "max-w-screen-lg"),
    ScreenExtraLarge("screen-xl", "max-w-screen-xl"),
    ScreenTwoExtraLarge("screen-2xl", "max-w-screen-2xl"),
    ;

    companion object {
        /** Rem scale plus fill / viewport / prose. These stay on `data-ff-max-width`. */
        @JvmField
        val TOKENS: Set<String> =
            entries.filter { it.widthClass == null }.map { it.token }.toCollection(linkedSetOf())

        /** Default panel content width (historical `.ff-page` cap of 80rem). */
        const val DEFAULT: String = "7xl"

        /**
         * PascalCase names from the first cut of this API. Still accepted.
         * `Prose` adds `max-w-prose`; the short token `prose` stays on `data-ff-max-width`.
         */
        private val LEGACY_CLASSES: Map<String, String> =
            linkedMapOf(
                "Prose" to "max-w-prose",
                "ScreenSmall" to "max-w-screen-sm",
                "ScreenMedium" to "max-w-screen-md",
                "ScreenLarge" to "max-w-screen-lg",
                "ScreenExtraLarge" to "max-w-screen-xl",
                "ScreenTwoExtraLarge" to "max-w-screen-2xl",
                "Container" to "container",
            )

        private val BY_TOKEN: Map<String, MaxWidth> = entries.associateBy { it.token }

        /**
         * One bracketed CSS length and nothing else: `[900px]`, `[48rem]`, `[80ch]`, `[0]`.
         */
        private val ARBITRARY =
            Regex(
                """\[(0|(?:\d+\.\d+|\d*\.\d+|\d+)(?:px|rem|em|ch|ex|vw|vh|vmin|vmax|dvw|dvh|svw|svh|lvw|lvh|cqw|cqh|%|cm|mm|in|pt|pc))\]""",
                RegexOption.IGNORE_CASE,
            )

        @JvmStatic
        fun requireKnown(width: String): String {
            if (BY_TOKEN.containsKey(width) || LEGACY_CLASSES.containsKey(width) || arbitraryLength(width) != null) {
                return width
            }
            throw FireflyException("Unknown max width [$width]")
        }

        /**
         * Class for [width], or null when [width] still uses `data-ff-max-width`.
         * A bracketed length becomes `max-w-[900px]` (brackets included).
         */
        @JvmStatic
        fun utilityClass(width: String): String? {
            val known = BY_TOKEN[width]
            if (known != null && known.widthClass != null) return known.widthClass
            val legacy = LEGACY_CLASSES[width]
            if (legacy != null) return legacy
            if (arbitraryLength(width) != null) return "max-w-$width"
            return null
        }

        /**
         * Inline custom property for a bracketed length, or null.
         * The panel stylesheet applies it from elements whose class is `max-w-[…]`.
         */
        @JvmStatic
        fun arbitraryStyle(width: String): String? {
            val length = arbitraryLength(width) ?: return null
            return "--ff-arbitrary-max: $length"
        }

        private fun arbitraryLength(width: String): String? {
            val match = ARBITRARY.matchEntire(width) ?: return null
            return match.groupValues[1]
        }
    }
}
