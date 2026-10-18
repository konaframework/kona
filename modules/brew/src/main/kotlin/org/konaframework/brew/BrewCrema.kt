package org.konaframework.brew

import org.konaframework.brew.features.SupportSlots
import org.konaframework.brew.features.recordIsland
import org.konaframework.brew.hydration.Coercion
import org.konaframework.crema.CremaEngine
import org.konaframework.crema.CremaTag

/**
 * Crema surface: `<brew:name … />`, `@brew`, `@island`, `@teleport`, `@placeholder`,
 * `@script`/`@assets`, `@entangle`, `@this`.
 */
internal object BrewCrema {
    fun install(engine: CremaEngine, manager: BrewManager) {
        engine.tag("brew") { tag -> renderTag(manager, tag) }
        engine.directive("brew") { call ->
            val args = call.arguments()
            require(args.isNotEmpty()) { "@brew requires a component name" }
            @Suppress("UNCHECKED_CAST")
            val params = (args.getOrNull(1) as? Map<String, Any?>) ?: emptyMap()
            manager.mount(args[0].toString(), params, args.getOrNull(2)?.toString())
        }
        engine.directive("brewStyles") { manager.assets.styles() }
        engine.directive("brewScripts") { manager.assets.scripts() }
        engine.directive("brewScriptConfig") { manager.assets.scriptConfig() }
        engine.directive("js") { call -> js(call.evaluate(call.expression)) }
        engine.directive("entangle") { call ->
            val args = call.arguments()
            val name = when {
                args.isNotEmpty() -> args[0].toString()
                else -> call.expression.trim().trim('\'', '"')
            }.replace("'", "\\'")
            "\$wire.\$entangle('$name')"
        }
        engine.directive("this") { "\$wire" }
        engine.directive("placeholder") { call ->
            val args = call.arguments()
            when {
                args.isNotEmpty() -> args[0].toString()
                !manager.config.lazyPlaceholder.isNullOrBlank() -> manager.config.lazyPlaceholder!!
                else -> "<div wire:lazy></div>"
            }
        }
        engine.blockDirective("script") { call ->
            collectBlock(manager, call.body.trim().removePrefix("<script>").removeSuffix("</script>").trim(), "scripts")
            ""
        }
        engine.blockDirective("assets") { call ->
            collectBlock(manager, call.expand().trim(), "assets")
            ""
        }
        engine.blockDirective("island") { call ->
            val name = islandName(call.expression, call.arguments())
            val html = call.expand()
            val current = manager.current()
            if (current != null) recordIsland(current, name, html, islandMeta(call.expression))
            """<div wire:island="$name">$html</div>"""
        }
        engine.blockDirective("teleport") { call ->
            val args = call.arguments()
            val selector = if (args.isNotEmpty()) args[0].toString() else call.expression.trim().trim('\'', '"')
            val body = call.expand()
            """<template wire:teleport="$selector">$body</template>"""
        }
        engine.blockDirective("slot") { call ->
            val args = call.arguments()
            val name = if (args.isNotEmpty()) args[0].toString() else "default"
            val html = call.expand()
            val current = manager.current()
            if (current != null) SupportSlots.put(current, name, html)
            html
        }
        engine.blockDirective("persist") { call ->
            val args = call.arguments()
            val fromArgs = args.firstOrNull()?.toString().orEmpty()
            val fromExpr = call.expression.trim().trim('\'', '"')
            val name = listOf(fromArgs, fromExpr, "default").first { it.isNotBlank() }
            """<div wire:persist="$name">${call.expand()}</div>"""
        }
    }

    internal fun islandName(expression: String, args: List<Any?>): String {
        if (args.isNotEmpty()) {
            val fromArgs = args[0]?.toString().orEmpty()
            if (fromArgs.isNotBlank() && fromArgs != "null") return fromArgs
        }
        val named = expression.substringAfter("name:", missingDelimiterValue = "")
            .substringBefore(',')
            .trim()
            .trim('\'', '"')
        return if (named.isBlank()) "default" else named
    }

    internal fun islandMeta(expression: String): Map<String, Any?> {
        val meta = linkedMapOf<String, Any?>()
        if (expression.contains("lazy")) meta["lazy"] = true
        if (expression.contains("defer")) meta["defer"] = true
        if (expression.contains("always")) meta["always"] = true
        if (expression.contains("skip")) meta["skip"] = true
        return meta
    }

    private fun collectBlock(manager: BrewManager, source: String, key: String) {
        if (source.isEmpty()) return
        val component = manager.current() ?: return
        @Suppress("UNCHECKED_CAST")
        val list = component.state().store.getOrPut(key) { mutableListOf<String>() } as MutableList<String>
        list += source
    }

    private fun renderTag(manager: BrewManager, tag: CremaTag): String {
        val name = resolveTagName(tag)
        val params = LinkedHashMap<String, Any?>()
        var key: String? = null
        for ((attr, value) in tag.attributes) {
            when (attr) {
                "key", "wire:key" -> key = value?.toString()
                "is", "component" -> Unit
                else -> params[if (attr.contains('-') && !attr.contains(':')) camel(attr) else attr] = value
            }
        }
        for ((slotName, slotHtml) in tag.slots) {
            params["__slot_$slotName"] = slotHtml
        }
        if (tag.slot.isNotBlank()) params["__slot_default"] = tag.slot
        return manager.mount(name, params, key)
    }

    private fun resolveTagName(tag: CremaTag): String {
        if (tag.name != "dynamic-component" && tag.name != "is") return tag.name
        val fromIs = tag.attributes["is"]?.toString()
        if (fromIs != null) return fromIs
        val fromComponent = tag.attributes["component"]?.toString()
        if (fromComponent != null) return fromComponent
        error("<brew:dynamic-component> requires :is or :component")
    }

    private val KEBAB = Regex("-+([a-zA-Z0-9])")

    private fun camel(value: String): String = KEBAB.replace(value) { it.groupValues[1].uppercase() }

    /** `Js::from` — safe JavaScript expression for embedding in HTML attributes / scripts. */
    fun js(value: Any?): String {
        val json = Coercion.mapper.writeValueAsString(value)
            .replace("<", "\\u003C").replace(">", "\\u003E").replace("&", "\\u0026").replace("'", "\\u0027")
        return when {
            json.startsWith("{") || json.startsWith("[") -> "JSON.parse('${json.replace("\\", "\\\\").replace("\"", "\\u0022")}')"
            json.startsWith("\"") -> "'" + json.substring(1, json.length - 1).replace("\\\"", "\\u0022") + "'"
            else -> json
        }
    }
}
