package org.konaframework.wire.hydration

import org.konaframework.wire.CorruptComponentPayloadException
import org.konaframework.wire.Form
import org.konaframework.wire.FormMeta
import org.konaframework.wire.WireException
import java.lang.reflect.Type

/**
 * Keeps a [Form] instance (and its component attachment) stable across nested `wire:model` writes.
 * Public fields travel in the snapshot; the owner reference stays on the server.
 */
internal object FormSynth : Synth() {
    override val key = "frm"

    override fun match(value: Any): Boolean = value is Form

    override fun dehydrate(value: Any, context: SynthContext): Pair<Any?, Map<String, Any?>> {
        val form = value as Form
        val out = LinkedHashMap<String, Any?>()
        for ((name, field) in FormMeta.of(form).fields) {
            out[name] = context.dehydrateChild(name, field.get(form))
        }
        return out to mapOf("class" to form.javaClass.name)
    }

    override fun hydrate(value: Any?, meta: Map<String, Any?>, context: SynthContext, type: Type?): Any? {
        val cls = context.policy().load(meta["class"] as? String, FormSynth::class.java.classLoader)
        if (!Form::class.java.isAssignableFrom(cls)) {
            throw CorruptComponentPayloadException("Class [${cls.name}] is not a form object")
        }
        val form = try {
            cls.getDeclaredConstructor().apply { isAccessible = true }.newInstance() as Form
        } catch (e: ReflectiveOperationException) {
            throw WireException("Form [${cls.simpleName}] requires a public no-arg constructor", e)
        }
        val raw = value as? Map<*, *> ?: return form
        val fields = FormMeta.of(form).fields
        for ((key, child) in raw) {
            val name = key.toString()
            val field = fields[name] ?: continue
            val hydrated = context.hydrateChild(name, child, field.genericType)
            field.set(form, Coercion.toType(hydrated, field.type, field.genericType))
        }
        return form
    }

    override fun childType(type: Type?, target: Any?, key: String): Type? {
        val form = target as? Form ?: return null
        return FormMeta.of(form).fields[key]?.genericType
    }

    override fun get(target: Any, key: String): Any? = FormMeta.of(target as Form).fields[key]?.get(target)

    override fun set(target: Any, key: String, value: Any?): Any {
        val form = target as Form
        val field = FormMeta.of(form).fields[key] ?: return form
        field.set(form, value)
        return form
    }

    override fun unset(target: Any, key: String): Any {
        val form = target as Form
        val field = FormMeta.of(form).fields[key] ?: return form
        val fresh = form.javaClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance() as Form
        field.set(form, field.get(fresh))
        return form
    }
}
