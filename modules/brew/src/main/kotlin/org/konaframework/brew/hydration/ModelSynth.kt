package org.konaframework.brew.hydration

import org.konaframework.brew.CorruptComponentPayloadException
import org.konaframework.cadence.Cadence
import java.lang.reflect.Type

/**
 * Cadence model synthesizer — dehydrates to `{class, key}` and re-queries on hydrate
 * (`SupportModels` / Eloquent model synth).
 */
object ModelSynth : Synth() {
    override val key: String = "mdl"

    override fun match(value: Any): Boolean = value is Cadence<*>

    override fun dehydrate(value: Any, context: SynthContext): Pair<Any?, Map<String, Any?>> {
        val model = value as Cadence<*>
        return null to linkedMapOf(
            "class" to model.javaClass.name,
            "key" to model.getKey(),
            "connection" to model.connectionName,
        )
    }

    override fun hydrate(value: Any?, meta: Map<String, Any?>, context: SynthContext, type: Type?): Any? {
        val classRaw = meta["class"] ?: return null
        val className = classRaw.toString()
        val key = meta["key"] ?: return null
        if (!context.policy().isAllowed(className)) {
            throw CorruptComponentPayloadException("Class [$className] is not allowed in Brew snapshots")
        }
        val typeClass = Class.forName(className)
        val companion = Cadence::class.java.getMethod("of", Class::class.java).invoke(null, typeClass)
        return companion.javaClass.getMethod("find", Any::class.java).invoke(companion, key)
    }
}

/** Collection of Cadence models dehydrated as a list of model tuples. */
object ModelCollectionSynth : Synth() {
    override val key: String = "mdlc"

    override fun match(value: Any): Boolean =
        value is Collection<*> && value.isNotEmpty() && value.all { it is Cadence<*> }

    override fun dehydrate(value: Any, context: SynthContext): Pair<Any?, Map<String, Any?>> {
        @Suppress("UNCHECKED_CAST")
        val list = (value as Collection<*>).mapIndexed { i, item ->
            context.dehydrateChild(i.toString(), item)
        }
        return list to emptyMap()
    }

    override fun hydrate(value: Any?, meta: Map<String, Any?>, context: SynthContext, type: Type?): Any? {
        val list = value as? List<*> ?: return emptyList<Any?>()
        return list.mapIndexed { i, item -> context.hydrateChild(i.toString(), item, null) }
    }
}
