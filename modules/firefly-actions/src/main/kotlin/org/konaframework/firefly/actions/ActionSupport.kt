package org.konaframework.firefly.actions

import org.konaframework.firefly.schemas.Component
import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException
import java.util.function.Function
import java.util.function.Predicate

internal fun escapeHtml(text: String): String = buildString {
    for (ch in text) {
        if (ch == '&') append("&amp;")
        else if (ch == '<') append("&lt;")
        else if (ch == '>') append("&gt;")
        else if (ch == '"') append("&quot;")
        else append(ch)
    }
}

internal class TextSetting {
    private var literal: String? = null
    private var using: Function<Get, String>? = null

    fun setLiteral(value: String) {
        literal = value
        using = null
    }

    fun setUsing(provider: Function<Get, String>) {
        using = provider
        literal = null
    }

    fun isSet(): Boolean = using != null || literal != null

    fun clear() {
        literal = null
        using = null
    }

    fun resolve(get: Get): String? {
        val provider = using
        if (provider != null) return provider.apply(get)
        return literal
    }
}

internal class Flag(private var literal: Boolean = false) {
    private var using: Predicate<Get>? = null

    fun setLiteral(value: Boolean) {
        literal = value
        using = null
    }

    fun setUsing(predicate: Predicate<Get>) {
        using = predicate
        literal = false
    }

    fun resolve(get: Get): Boolean {
        val predicate = using
        if (predicate != null) return predicate.test(get)
        return literal
    }
}

internal class Authorization {
    private var literal: Boolean? = null
    private var using: Predicate<Get>? = null
    private var ability: String? = null

    fun allow(on: Boolean) {
        literal = on
        using = null
    }

    fun allow(predicate: Predicate<Get>) {
        using = predicate
        literal = null
    }

    fun ability(name: String) {
        if (name.isEmpty()) throw FireflyException("Authorization ability is empty")
        ability = name
    }

    fun permits(schema: Schema, component: Component): Boolean {
        val get = SchemaBridge.reader(schema, component)
        val predicate = using
        if (predicate != null && !predicate.test(get)) return false
        if (literal == false) return false
        val name = ability
        if (name == null) return true
        val gate = ActionGateHolder.current
        if (gate == null) return false
        return gate.allows(name, component, schema)
    }
}

internal object ActionGateHolder {
    var current: ActionGate? = null
}

internal object SchemaBridge {
    private val getFor = Schema::class.java.methods.first { it.name.startsWith("getFor$") }

    fun reader(schema: Schema, component: Component): Get {
        return getFor.invoke(schema, component) as Get
    }
}

internal fun Component.storedLabel(get: Get): String? {
    val field = Component::class.java.getDeclaredField("labelValue")
    field.isAccessible = true
    val value = field.get(this)
    val resolve = value.javaClass.getMethod("resolve", Get::class.java)
    return resolve.invoke(value, get) as String?
}

internal fun Component.storedView(): String? {
    val field = Component::class.java.getDeclaredField("viewName")
    field.isAccessible = true
    return field.get(this) as String?
}

internal fun Component.disabledNow(schema: Schema): Boolean {
    val type = Component::class.java
    val usingField = type.getDeclaredField("disabledUsing")
    usingField.isAccessible = true
    val using = usingField.get(this) as Predicate<Get>?
    if (using != null) return using.test(SchemaBridge.reader(schema, this))
    val flagField = type.getDeclaredField("disabledFlag")
    flagField.isAccessible = true
    return flagField.getBoolean(this)
}

internal fun triggerStyle(color: String?, size: String?): String {
    val parts = ArrayList<String>()
    if (color == "primary") parts.add("color:var(--ff-primary)")
    else if (color == "accent") parts.add("color:var(--ff-accent)")
    else if (color == "heading") parts.add("color:var(--ff-heading)")
    if (size == "xs") parts.add("font-size:0.75rem")
    else if (size == "sm") parts.add("font-size:0.875rem")
    else if (size == "md") parts.add("font-size:1rem")
    else if (size == "lg") parts.add("font-size:1.125rem")
    else if (size == "xl") parts.add("font-size:1.25rem")
    return parts.joinToString(";")
}

private val ACTION_NAME = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

internal fun requireActionName(name: String) {
    if (!ACTION_NAME.matches(name)) throw FireflyException("Invalid action name [$name]")
}
