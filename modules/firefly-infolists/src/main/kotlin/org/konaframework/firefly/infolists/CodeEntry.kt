package org.konaframework.firefly.infolists

import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import java.util.function.Consumer
import java.util.function.Function

class CodeEntry private constructor(name: String) : Entry<CodeEntry>(name) {
    private val grammarValue = TextSetting()
    private val lightValue = TextSetting()
    private val darkValue = TextSetting()
    private val flagsValue = IntSetting()

    override fun kind(): String = "code"

    fun grammar(value: String): CodeEntry = apply { grammarValue.set(value) }

    fun grammar(value: Function<Get, String>): CodeEntry = apply { grammarValue.set(value) }

    fun language(value: String): CodeEntry = grammar(value)

    fun language(value: Function<Get, String>): CodeEntry = grammar(value)

    fun lightTheme(value: String): CodeEntry = apply { lightValue.set(value) }

    fun lightTheme(value: Function<Get, String>): CodeEntry = apply { lightValue.set(value) }

    fun darkTheme(value: String): CodeEntry = apply { darkValue.set(value) }

    fun darkTheme(value: Function<Get, String>): CodeEntry = apply { darkValue.set(value) }

    fun jsonFlags(flags: Int): CodeEntry = apply { flagsValue.set(flags) }

    fun jsonFlags(flags: Function<Get, Int>): CodeEntry = apply { flagsValue.set(flags) }

    override fun renderEntry(schema: Schema, get: Get): String {
        val state = resolvedState(schema, get)
        if (isBlank(state)) {
            return """<span class="ff-placeholder">${escapeHtml(placeholderValue.get(get) ?: "")}</span>"""
        }
        val encoded = encoded(state, get)
        val grammar = grammarValue.get(get) ?: defaultGrammar(state)
        val light = lightValue.get(get) ?: "github-light"
        val dark = darkValue.get(get) ?: "github-dark"
        val payload = """{"engine":"shiki","grammar":"${escapeHtml(grammar)}","light":"${escapeHtml(light)}","dark":"${escapeHtml(dark)}"}"""
        return """<div class="ff-code" data-ff-shiki="shiki" data-ff-grammar="${escapeHtml(grammar)}" data-ff-light-theme="${escapeHtml(light)}" data-ff-dark-theme="${escapeHtml(dark)}" x-data="ffEntry"><pre><code>${escapeHtml(encoded)}</code></pre><script type="application/json" class="ff-shiki-payload">$payload</script></div>"""
    }

    private fun encoded(state: Any?, get: Get): String {
        if (state is Map<*, *> || state is Iterable<*>) {
            return toJson(state, flagsValue.get(get) ?: 0)
        }
        return stringify(state)
    }

    private fun defaultGrammar(state: Any?): String {
        if (state is Map<*, *> || state is Iterable<*>) return "json"
        return "html"
    }

    companion object {
        @JvmStatic
        fun make(name: String): CodeEntry {
            val entry = CodeEntry(name)
            finish(entry)
            return entry
        }

        @JvmStatic
        fun configureUsing(block: Consumer<CodeEntry>?) = configure(CodeEntry::class.java, block)
    }
}
