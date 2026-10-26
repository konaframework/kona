package org.konaframework.wire.hydration

import org.konaframework.cadence.Cadence
import org.konaframework.cadence.Database
import org.konaframework.wire.CorruptComponentPayloadException
import java.lang.reflect.Type

/**
 * Cadence model synthesizer. The snapshot stores the class, primary key, connection, and
 * attributes. When a global database is configured, hydrate reloads the row by key. Otherwise it
 * rebuilds the model from the stored attributes so a template can keep calling `getEmail()` and
 * `getAttribute`.
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
            "attributes" to model.getAttributes(),
        )
    }

    override fun hydrate(value: Any?, meta: Map<String, Any?>, context: SynthContext, type: Type?): Any? {
        val classRaw = meta["class"] ?: return null
        val className = classRaw.toString()
        if (!context.policy().isAllowed(className)) {
            throw CorruptComponentPayloadException("Class [$className] is not allowed in Wire snapshots")
        }
        val typeClass = Class.forName(className)
        if (!Cadence::class.java.isAssignableFrom(typeClass)) return null
        val attributes = meta["attributes"] as? Map<*, *>
        val key = meta["key"]
        if (key != null && Database.hasGlobal()) {
            val companion = Cadence::class.java.getMethod("of", Class::class.java).invoke(null, typeClass)
            return companion.javaClass.getMethod("find", Any::class.java).invoke(companion, key)
        }
        if (attributes == null) return null
        return restore(typeClass, attributes, key)
    }

    private fun restore(typeClass: Class<*>, attributes: Map<*, *>, key: Any?): Cadence<*> {
        val ctor = try {
            typeClass.getDeclaredConstructor()
        } catch (e: NoSuchMethodException) {
            throw CorruptComponentPayloadException("Class [${typeClass.name}] has no no-arg constructor")
        }
        ctor.isAccessible = true
        val model = ctor.newInstance() as Cadence<*>
        for ((name, attr) in attributes) {
            if (name is String) model.setAttribute(name, attr)
        }
        if (key != null && model.getKey() == null) model.setAttribute(model.primaryKey, key)
        return model
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
