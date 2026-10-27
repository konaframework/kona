package org.konaframework.firefly.tables

import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.Function

open class Filter(val name: String) {
    private val labelValue = TextSetting()
    private val indicatorValue = TextSetting()
    private val defaultValue = ValueSetting()
    private var attribute: String? = null
    private var operator = "="
    private var queryHook: BiConsumer<Any, Any?>? = null
    private var options: Map<String, String> = emptyMap()
    private var optionsUsing: Function<Cell, Map<String, String>>? = null
    private val inputExtras = InputAttributes()

    fun label(value: String): Filter = apply { labelValue.set(value) }

    fun label(value: Function<Cell, String>): Filter = apply { labelValue.set(value) }

    fun indicator(value: String): Filter = apply { indicatorValue.set(value) }

    fun indicator(value: Function<Cell, String>): Filter = apply { indicatorValue.set(value) }

    fun default(value: Any?): Filter = apply { defaultValue.set(value) }

    fun defaultUsing(value: java.util.function.Function<Cell, Any?>): Filter = apply { defaultValue.set(value) }

    fun attribute(name: String): Filter = apply { attribute = name }

    fun operator(value: String): Filter = apply { operator = value }

    fun query(hook: BiConsumer<Any, Any?>): Filter = apply { queryHook = hook }

    fun options(values: Map<String, String>): Filter = apply {
        options = LinkedHashMap(values)
        optionsUsing = null
    }

    fun options(provider: Function<Cell, Map<String, String>>): Filter = apply { optionsUsing = provider }

    fun extraInputAttributes(attributes: Map<String, String>): Filter = apply { inputExtras.set(attributes) }

    fun extraInputAttributes(provider: Function<Cell, Map<String, String>>): Filter = apply { inputExtras.set(provider) }

    open fun heading(cell: Cell): String = labelValue.get(cell, name)

    fun defaultValue(cell: Cell): Any? = defaultValue.get(cell)

    open fun apply(query: Any, store: RecordStore<*, *>, value: Any?) {
        if (!active(value)) return
        val hook = queryHook
        if (hook != null) {
            hook.accept(query, value)
            return
        }
        val column = attribute ?: return
        @Suppress("UNCHECKED_CAST")
        (store as RecordStore<Any?, Any?>).constrain(query, column, operator, value)
    }

    open fun render(cell: Cell, value: Any?): String {
        val current = value?.toString().orEmpty()
        val choices = resolvedOptions(cell)
        val tags = if (choices.isEmpty()) {
            ""
        } else {
            choices.entries.joinToString("") { (option, label) ->
                val selected = if (option == current) """ selected""" else ""
                """<option value="${escapeHtml(option)}"$selected>${escapeHtml(label)}</option>"""
            }
        }
        val extra = inputExtras.markup(cell)
        // Named controls submit with the toolbar GET form; chrome soft-nav swaps the wrap.
        val control = if (choices.isEmpty()) {
            """<input name="${escapeHtml(name)}" data-ff-filter="${escapeHtml(name)}" value="${escapeHtml(current)}"$extra>"""
        } else {
            """<select name="${escapeHtml(name)}" data-ff-filter="${escapeHtml(name)}" data-ff-table-nav-select$extra>$tags</select>"""
        }
        val indicator = if (!active(value)) {
            ""
        } else {
            val text = indicatorValue.get(cell, current)
            """<span class="ff-filter-indicator">${escapeHtml(text)}</span>"""
        }
        return """<label class="ff-filter">${escapeHtml(heading(cell))}$control$indicator</label>"""
    }

    protected fun active(value: Any?): Boolean = value != null && value != ""

    private fun resolvedOptions(cell: Cell): Map<String, String> {
        val provider = optionsUsing
        if (provider != null) return provider.apply(cell)
        return options
    }

    companion object {
        private val configurers = HashMap<Class<*>, Consumer<Filter>?>()

        @JvmStatic
        fun make(name: String): Filter = finish(Filter(name))

        @JvmStatic
        fun configureUsing(type: Class<out Filter>, block: Consumer<Filter>?) {
            configurers[type] = block
        }

        @JvmStatic
        fun configureUsing(block: Consumer<Filter>?) {
            configureUsing(Filter::class.java, block)
        }

        internal fun <T : Filter> finish(filter: T): T {
            configurers[filter.javaClass]?.accept(filter)
            return filter
        }
    }
}

open class SelectFilter private constructor(name: String) : Filter(name) {
    companion object {
        @JvmStatic
        fun make(name: String): SelectFilter = finish(SelectFilter(name))

        @JvmStatic
        fun configureUsing(block: Consumer<SelectFilter>?) {
            if (block == null) {
                Filter.configureUsing(SelectFilter::class.java, null)
            } else {
                @Suppress("UNCHECKED_CAST")
                Filter.configureUsing(SelectFilter::class.java, block as Consumer<Filter>?)
            }
        }
    }
}

open class TernaryFilter private constructor(name: String) : Filter(name) {
    override fun render(cell: Cell, value: Any?): String {
        val current = value?.toString().orEmpty()
        val buttons = listOf("true", "false", "blank").joinToString("") { option ->
            val pressed = if (option == current) "true" else "false"
            """<button type="button" aria-pressed="$pressed" data-ff-ternary="$option">$option</button>"""
        }
        return """<div class="ff-filter ff-ternary">${escapeHtml(heading(cell))}$buttons</div>"""
    }

    override fun apply(query: Any, store: RecordStore<*, *>, value: Any?) {
        if (!active(value)) return
        val column = name
        @Suppress("UNCHECKED_CAST")
        val typed = store as RecordStore<Any?, Any?>
        when (value) {
            "blank" -> typed.constrain(query, column, "null", null)
            "true", true -> typed.constrain(query, column, "=", true)
            else -> typed.constrain(query, column, "=", false)
        }
    }

    companion object {
        @JvmStatic
        fun make(name: String): TernaryFilter = finish(TernaryFilter(name))
    }
}

open class TrashedFilter private constructor() : Filter("trashed") {
    override fun apply(query: Any, store: RecordStore<*, *>, value: Any?) {
        if (!active(value)) return
        @Suppress("UNCHECKED_CAST")
        (store as RecordStore<Any?, Any?>).trashed(query, value.toString())
    }

    companion object {
        @JvmStatic
        fun make(): TrashedFilter = finish(TrashedFilter())
    }
}
