package org.konaframework.support.str

import java.util.Locale

/**
 * English noun pluralizer behind [Str.plural]. Only the last word of the input is inflected, so
 * `blog_post`, `BlogPost`, and `blog-post` keep their prefix; case of the original word is kept.
 */
internal object Pluralizer {
    private val LAST_WORD = Regex("[A-Z]?[a-z]+$|[A-Z]+$")

    private val UNCOUNTABLE = setOf(
        "audio", "bison", "cattle", "chassis", "compensation", "coreopsis", "data", "deer", "education",
        "emoji", "equipment", "evidence", "feedback", "firmware", "fish", "furniture", "gold", "hardware",
        "information", "jedi", "kin", "knowledge", "love", "metadata", "money", "moose", "news", "nutrition",
        "offspring", "plankton", "pokemon", "police", "rain", "rice", "series", "sheep", "software",
        "species", "swine", "traffic", "wheat",
    )

    private val IRREGULAR = mapOf(
        "atlas" to "atlases",
        "cafe" to "cafes",
        "canvas" to "canvases",
        "gas" to "gases",
        "iris" to "irises",
        "lens" to "lenses",
        "child" to "children",
        "cookie" to "cookies",
        "criterion" to "criteria",
        "curriculum" to "curricula",
        "datum" to "data",
        "foot" to "feet",
        "german" to "germans",
        "goose" to "geese",
        "human" to "humans",
        "medium" to "media",
        "memorandum" to "memoranda",
        "move" to "moves",
        "ox" to "oxen",
        "person" to "people",
        "roman" to "romans",
        "safe" to "safes",
        "sex" to "sexes",
        "tooth" to "teeth",
        "valve" to "valves",
        "wave" to "waves",
        "zombie" to "zombies",
    )

    private val IRREGULAR_PLURALS = IRREGULAR.values.toSet()

    private val RULES: List<Pair<Regex, String>> = listOf(
        Regex("(quiz)$") to "$1zes",
        Regex("^([ml])ouse$") to "$1ice",
        Regex("(matr|vert|ind)(ix|ex)$") to "$1ices",
        Regex("(x|ch|ss|sh)$") to "$1es",
        Regex("([^aeiouy]|qu)y$") to "$1ies",
        Regex("(hive|gulf)$") to "$1s",
        Regex("([^f])fe$") to "$1ves",
        Regex("([lr])f$") to "$1ves",
        Regex("sis$") to "ses",
        Regex("(tax)on$") to "$1a",
        Regex("(m)an$") to "$1en",
        Regex("(buffal|her|potat|tomat|volcan)o$") to "$1oes",
        Regex("(alumn|bacill|cact|foc|fung|nucle|radi|stimul|syllab|termin)us$") to "$1i",
        Regex("us$") to "uses",
        Regex("(alias)$") to "$1es",
        Regex("(ax|cris|test)is$") to "$1es",
        Regex("s$") to "s",
    )

    fun plural(value: String): String {
        val match = LAST_WORD.find(value)
        if (match == null) {
            return if (value.isEmpty()) value else value + "s"
        }
        val word = match.value
        val prefix = value.substring(0, value.length - word.length)
        return prefix + matchCase(inflect(word.lowercase(Locale.ROOT)), word)
    }

    private fun inflect(word: String): String {
        if (word in UNCOUNTABLE || word in IRREGULAR_PLURALS) {
            return word
        }
        IRREGULAR[word]?.let { return it }
        for ((pattern, replacement) in RULES) {
            if (pattern.containsMatchIn(word)) {
                return pattern.replace(word, replacement)
            }
        }
        return word + "s"
    }

    private fun matchCase(plural: String, original: String): String = when {
        original.length > 1 && original == original.uppercase(Locale.ROOT) -> plural.uppercase(Locale.ROOT)
        original[0].isUpperCase() -> plural[0].uppercaseChar() + plural.substring(1)
        else -> plural
    }
}
