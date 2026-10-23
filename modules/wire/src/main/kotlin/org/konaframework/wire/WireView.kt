package org.konaframework.wire

/**
 * What a component renders: a named Crema view, inline Crema source, or raw HTML.
 */
class WireView private constructor(
    /** Crema view name (null for inline / raw). */
    val name: String?,
    /** Inline Crema source (null for named / raw). */
    val source: String?,
    /** Pre-rendered HTML used verbatim. */
    val html: String?,
    data: Map<String, Any?>,
) {
    val data: MutableMap<String, Any?> = LinkedHashMap(data)
    /** Layout view name (`->layout('layouts.app')`). */
    var layoutName: String? = null
        private set
    /** Document title (`->title('…')`). */
    var titleText: String? = null
        private set
    /** Named layout slots (`->slot('sidebar', html)`). */
    val slots: MutableMap<String, String> = linkedMapOf()

    /** Add view data (`->with('key', $value)`). */
    fun with(key: String, value: Any?): WireView {
        data[key] = value
        return this
    }

    fun with(values: Map<String, Any?>): WireView {
        data.putAll(values)
        return this
    }

    /** Wrap this view in a layout (`->layout('layouts.app')`). */
    fun layout(name: String): WireView {
        layoutName = name
        return this
    }

    /** Set the document title (`->title('Dashboard')`). */
    fun title(value: String): WireView {
        titleText = value
        return this
    }

    /** Fill a named layout slot (`->slot('sidebar', html)`). */
    fun slot(name: String, html: String): WireView {
        slots[name] = html
        return this
    }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun named(name: String, data: Map<String, Any?> = emptyMap()): WireView = WireView(name, null, null, data)

        @JvmStatic
        @JvmOverloads
        fun inline(@CremaTemplate source: String, data: Map<String, Any?> = emptyMap()): WireView =
            WireView(null, source, null, data)

        @JvmStatic
        fun raw(html: String): WireView = WireView(null, null, html, emptyMap())
    }
}
