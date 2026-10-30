package org.konaframework.icons

/**
 * A resolved SVG icon with mutable HTML attributes (Blade Icons `Svg` shape).
 */
class Svg internal constructor(
    private val iconName: String,
    private val rawContents: String,
    attributes: Map<String, String>,
) {
    private val attributes = LinkedHashMap(attributes)

    fun name(): String = iconName

    fun contents(): String = rawContents

    /** Append or replace the `class` attribute. */
    fun className(value: String): Svg {
        val existing = attributes["class"]
        attributes["class"] = if (existing.isNullOrBlank()) value else "$existing $value".trim()
        return this
    }

    fun attr(name: String, value: String): Svg {
        if (name.equals("class", ignoreCase = true)) {
            return className(value)
        }
        attributes[name] = value
        return this
    }

    fun attributes(values: Map<String, String>): Svg {
        for ((key, value) in values) {
            attr(key, value)
        }
        return this
    }

    fun toHtml(): String = mergeSvgAttributes(rawContents, attributes)

    override fun toString(): String = toHtml()
}
