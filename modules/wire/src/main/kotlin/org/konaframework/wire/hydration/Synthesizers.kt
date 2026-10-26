package org.konaframework.wire.hydration

import org.konaframework.wire.CorruptComponentPayloadException
import org.konaframework.wire.Wireable
import java.lang.reflect.Type
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.MonthDay
import java.time.OffsetDateTime
import java.time.Year
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.temporal.TemporalAccessor
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Registry of synthesizers (`HandleSynths`). Custom synths registered with [register] take precedence over built-ins.
 */
class Synthesizers {
    val policy = SecurityPolicy()
    private val custom = CopyOnWriteArrayList<Synth>()
    private val builtIns: List<Synth> = listOf(
        EnumSynth, WireableSynth, TemporalSynth, BigNumberSynth, UuidSynth, ArraySynth, SetSynth, MapSynth, FormSynth, ObjectSynth,
    )

    fun register(synth: Synth): Synthesizers {
        custom.add(0, synth)
        return this
    }

    private fun all(): Sequence<Synth> = custom.asSequence() + builtIns.asSequence()

    fun byKey(key: String): Synth =
        all().firstOrNull { it.key == key } ?: throw CorruptComponentPayloadException("Unknown synthesizer [$key]")

    /** Synth for a runtime value (null for JSON scalars). */
    fun forValue(value: Any?): Synth? {
        if (value == null || isScalar(value)) return null
        return all().first { it.match(value) }
    }

    fun context(path: String = ""): SynthContext = SynthContext(this, path)

    fun dehydrate(value: Any?): Any? = dehydrate(value, context())

    fun dehydrate(value: Any?, context: SynthContext): Any? {
        if (value is Long && (value > MAX_SAFE || value < -MAX_SAFE)) return listOf(value.toString(), mapOf("s" to "num", "class" to "long"))
        if (value is Char) return value.toString()
        if (value is Float) return value.toString().toDouble()
        val synth = forValue(value) ?: return value
        val (data, meta) = synth.dehydrate(value!!, context)
        return listOf(data, LinkedHashMap<String, Any?>().apply { put("s", synth.key); putAll(meta) })
    }

    fun hydrate(value: Any?, type: Type?): Any? = hydrate(value, context(), type)

    fun hydrate(value: Any?, context: SynthContext, type: Type?): Any? {
        if (!isTuple(value)) {
            val raw = Synth.rawClass(type)
            return if (raw == null || value == null) value else Coercion.toType(value, raw, type!!)
        }
        val tuple = value as List<*>
        @Suppress("UNCHECKED_CAST")
        val meta = tuple[1] as Map<String, Any?>
        return byKey(meta["s"].toString()).hydrate(tuple[0], meta, context, type)
    }

    /** Read [path] segments starting from [root]. */
    fun getPath(root: Any?, segments: List<String>): Any? {
        var current = root
        for (segment in segments) {
            current = current?.let { forValue(it)?.get(it, segment) }
        }
        return current
    }

    /**
     * Set a nested value under [root] following [segments]; missing intermediate containers are created as maps.
     * [type] is the declared type of [root]. Returns the new root.
     */
    fun setPath(root: Any?, type: Type?, segments: List<String>, value: Any?, remove: Boolean): Any {
        val head = segments.first()
        val container = root ?: linkedMapOf<String, Any?>()
        val synth = forValue(container)
            ?: throw IllegalArgumentException("Cannot set nested key [$head] on a scalar value")
        val childType = synth.childType(type, container, head)
        if (segments.size == 1) {
            if (remove) return synth.unset(container, head)
            val raw = Synth.rawClass(childType)
            val coerced = if (raw == null || value == null) value else Coercion.toType(value, raw, childType!!)
            return synth.set(container, head, coerced)
        }
        val child = synth.get(container, head)
        return synth.set(container, head, setPath(child, childType, segments.drop(1), value, remove))
    }

    companion object {
        const val REMOVE_MARKER = "__rm__"
        private const val MAX_SAFE = 9007199254740991L

        @JvmStatic
        fun isScalar(value: Any): Boolean =
            value is String || value is Boolean || value is Int || value is Short || value is Byte ||
                value is Double || value is Float || value is Long || value is Char

        @JvmStatic
        fun isTuple(value: Any?): Boolean =
            value is List<*> && value.size == 2 && (value[1] as? Map<*, *>)?.containsKey("s") == true
    }
}

internal object ArraySynth : Synth() {
    override val key = "arr"

    override fun match(value: Any): Boolean = value is List<*> || value is Array<*> || value is Collection<*> && value !is Set<*>

    override fun dehydrate(value: Any, context: SynthContext): Pair<Any?, Map<String, Any?>> {
        val items = if (value is Array<*>) value.asList() else (value as Collection<*>).toList()
        return items.mapIndexed { i, item -> context.dehydrateChild(i.toString(), item) } to emptyMap()
    }

    override fun hydrate(value: Any?, meta: Map<String, Any?>, context: SynthContext, type: Type?): Any? {
        val elementType = elementType(type)
        val list = (value as List<*>).mapIndexedTo(ArrayList()) { i, item -> context.hydrateChild(i.toString(), item, elementType) }
        val raw = rawClass(type)
        if (raw != null && raw.isArray) {
            val array = java.lang.reflect.Array.newInstance(raw.componentType, list.size)
            list.forEachIndexed { i, item -> java.lang.reflect.Array.set(array, i, item) }
            return array
        }
        return list
    }

    private fun elementType(type: Type?): Type? {
        val raw = rawClass(type)
        if (raw != null && raw.isArray) return raw.componentType
        return typeArgument(type, 0)
    }

    override fun childType(type: Type?, target: Any?, key: String): Type? = elementType(type)

    override fun get(target: Any, key: String): Any? = key.toIntOrNull()?.let { toList(target).getOrNull(it) }

    override fun set(target: Any, key: String, value: Any?): Any {
        val list = toList(target).toMutableList()
        val index = key.toIntOrNull() ?: throw IllegalArgumentException("List index expected, got [$key]")
        if (index == list.size) list.add(value) else list[index] = value
        return restore(target, list)
    }

    override fun unset(target: Any, key: String): Any {
        val list = toList(target).toMutableList()
        val index = key.toIntOrNull()
        if (index != null && index in list.indices) list.removeAt(index)
        return restore(target, list)
    }

    private fun toList(target: Any): List<Any?> = if (target is Array<*>) target.asList() else (target as Collection<*>).toList()

    private fun restore(original: Any, list: List<Any?>): Any {
        if (original is Array<*>) {
            val array = java.lang.reflect.Array.newInstance(original.javaClass.componentType, list.size)
            list.forEachIndexed { i, item -> java.lang.reflect.Array.set(array, i, item) }
            return array
        }
        return ArrayList(list)
    }
}

internal object SetSynth : Synth() {
    override val key = "set"

    override fun match(value: Any): Boolean = value is Set<*>

    override fun dehydrate(value: Any, context: SynthContext): Pair<Any?, Map<String, Any?>> =
        (value as Set<*>).mapIndexed { i, item -> context.dehydrateChild(i.toString(), item) } to emptyMap()

    override fun hydrate(value: Any?, meta: Map<String, Any?>, context: SynthContext, type: Type?): Any? =
        (value as List<*>).mapIndexedTo(LinkedHashSet()) { i, item -> context.hydrateChild(i.toString(), item, typeArgument(type, 0)) }

    override fun childType(type: Type?, target: Any?, key: String): Type? = typeArgument(type, 0)

    override fun get(target: Any, key: String): Any? = key.toIntOrNull()?.let { (target as Set<*>).elementAtOrNull(it) }

    override fun set(target: Any, key: String, value: Any?): Any {
        val list = (target as Set<*>).toMutableList()
        val index = key.toIntOrNull() ?: throw IllegalArgumentException("Set index expected, got [$key]")
        if (index >= list.size) list.add(value) else list[index] = value
        return LinkedHashSet(list)
    }

    override fun unset(target: Any, key: String): Any {
        val list = (target as Set<*>).toMutableList()
        val index = key.toIntOrNull()
        if (index != null && index in list.indices) list.removeAt(index)
        return LinkedHashSet(list)
    }
}

internal object MapSynth : Synth() {
    override val key = "map"

    override fun match(value: Any): Boolean = value is Map<*, *>

    override fun dehydrate(value: Any, context: SynthContext): Pair<Any?, Map<String, Any?>> {
        val out = LinkedHashMap<String, Any?>()
        for ((k, v) in value as Map<*, *>) {
            out[k.toString()] = context.dehydrateChild(k.toString(), v)
        }
        return out to emptyMap()
    }

    override fun hydrate(value: Any?, meta: Map<String, Any?>, context: SynthContext, type: Type?): Any? {
        val valueType = typeArgument(type, 1)
        val out = LinkedHashMap<String, Any?>()
        for ((k, v) in value as Map<*, *>) {
            out[k.toString()] = context.hydrateChild(k.toString(), v, valueType)
        }
        return out
    }

    override fun childType(type: Type?, target: Any?, key: String): Type? = typeArgument(type, 1)

    override fun get(target: Any, key: String): Any? = (target as Map<*, *>)[key]

    override fun set(target: Any, key: String, value: Any?): Any {
        val map = LinkedHashMap<Any?, Any?>(target as Map<*, *>)
        map[key] = value
        return map
    }

    override fun unset(target: Any, key: String): Any {
        val map = LinkedHashMap<Any?, Any?>(target as Map<*, *>)
        map.remove(key)
        return map
    }
}

internal object EnumSynth : Synth() {
    override val key = "enm"

    override fun match(value: Any): Boolean = value is Enum<*>

    override fun dehydrate(value: Any, context: SynthContext): Pair<Any?, Map<String, Any?>> =
        (value as Enum<*>).name to mapOf("class" to value.declaringJavaClass.name)

    override fun hydrate(value: Any?, meta: Map<String, Any?>, context: SynthContext, type: Type?): Any? {
        if (value == null) return null
        val declared = rawClass(type)
        val cls = if (declared != null && declared.isEnum) declared else loadEnum(meta["class"], context)
        return Coercion.toType(value, cls)
    }

    private fun loadEnum(name: Any?, context: SynthContext): Class<*> {
        val cls = context.policy().load(name as? String, EnumSynth::class.java.classLoader)
        if (!cls.isEnum) throw CorruptComponentPayloadException("Class [$name] is not an enum")
        return cls
    }
}

internal object TemporalSynth : Synth() {
    override val key = "dt"

    private val TYPES: Map<String, Class<*>> = listOf(
        LocalDate::class.java, LocalDateTime::class.java, LocalTime::class.java, Instant::class.java,
        OffsetDateTime::class.java, ZonedDateTime::class.java, YearMonth::class.java, Year::class.java,
        MonthDay::class.java, Duration::class.java, java.util.Date::class.java,
    ).associateBy { it.simpleName }

    override fun match(value: Any): Boolean = value is TemporalAccessor || value is Duration || value is java.util.Date

    override fun dehydrate(value: Any, context: SynthContext): Pair<Any?, Map<String, Any?>> {
        val text = if (value is java.util.Date) value.toInstant().toString() else value.toString()
        val type = if (value is java.util.Date) "Date" else value.javaClass.simpleName
        return text to mapOf("type" to type)
    }

    override fun hydrate(value: Any?, meta: Map<String, Any?>, context: SynthContext, type: Type?): Any? {
        if (value == null) return null
        val cls = TYPES[meta["type"]?.toString()]
            ?: throw CorruptComponentPayloadException("Unsupported temporal type [${meta["type"]}]")
        if (cls == java.util.Date::class.java) return java.util.Date.from(Instant.parse(value.toString()))
        return Coercion.toType(value, cls)
    }
}

internal object BigNumberSynth : Synth() {
    override val key = "num"

    override fun match(value: Any): Boolean = value is BigDecimal || value is BigInteger

    override fun dehydrate(value: Any, context: SynthContext): Pair<Any?, Map<String, Any?>> =
        value.toString() to mapOf("class" to if (value is BigDecimal) "decimal" else "integer")

    override fun hydrate(value: Any?, meta: Map<String, Any?>, context: SynthContext, type: Type?): Any? {
        if (value == null) return null
        val text = value.toString()
        return when (meta["class"]) {
            "decimal" -> BigDecimal(text)
            "integer" -> BigInteger(text)
            else -> text.toLong()
        }
    }
}

internal object UuidSynth : Synth() {
    override val key = "uuid"

    override fun match(value: Any): Boolean = value is UUID

    override fun dehydrate(value: Any, context: SynthContext): Pair<Any?, Map<String, Any?>> = value.toString() to emptyMap()

    override fun hydrate(value: Any?, meta: Map<String, Any?>, context: SynthContext, type: Type?): Any? =
        value?.let { UUID.fromString(it.toString()) }
}

internal object WireableSynth : Synth() {
    override val key = "wrbl"

    override fun match(value: Any): Boolean = value is Wireable

    override fun dehydrate(value: Any, context: SynthContext): Pair<Any?, Map<String, Any?>> =
        context.dehydrateChild("", (value as Wireable).toWire()) to mapOf("class" to value.javaClass.name)

    override fun hydrate(value: Any?, meta: Map<String, Any?>, context: SynthContext, type: Type?): Any? {
        val cls = context.policy().load(meta["class"] as? String, WireableSynth::class.java.classLoader)
        if (!Wireable::class.java.isAssignableFrom(cls)) {
            throw CorruptComponentPayloadException("Class [${cls.name}] is not Wireable")
        }
        val raw = context.hydrateChild("", value, null)
        val factory = listOf("fromWire", "fromBrew").firstNotNullOfOrNull { name ->
            cls.methods.firstOrNull {
                it.name == name && it.parameterCount == 1 && java.lang.reflect.Modifier.isStatic(it.modifiers)
            }
        } ?: throw IllegalStateException("Wireable [${cls.name}] must declare a static fromWire(Object) factory")
        return factory.invoke(null, raw)
    }
}

/** Data classes, records and POJOs: properties dehydrated recursively, rebuilt with Jackson. */
internal object ObjectSynth : Synth() {
    override val key = "obj"

    override fun match(value: Any): Boolean = true

    override fun dehydrate(value: Any, context: SynthContext): Pair<Any?, Map<String, Any?>> {
        @Suppress("UNCHECKED_CAST")
        val props = Coercion.mapper.convertValue(value, Map::class.java) as Map<String, Any?>
        val out = LinkedHashMap<String, Any?>()
        val fields = propertyValues(value)
        for (name in props.keys) {
            out[name] = context.dehydrateChild(name, fields[name] ?: props[name])
        }
        return out to mapOf("class" to value.javaClass.name)
    }

    override fun hydrate(value: Any?, meta: Map<String, Any?>, context: SynthContext, type: Type?): Any? {
        val cls = context.policy().load(meta["class"] as? String, ObjectSynth::class.java.classLoader)
        val raw = LinkedHashMap<String, Any?>()
        for ((k, v) in value as Map<*, *>) {
            raw[k.toString()] = context.hydrateChild(k.toString(), v, fieldType(cls, k.toString()))
        }
        return Coercion.mapper.convertValue(raw, cls)
    }

    override fun childType(type: Type?, target: Any?, key: String): Type? =
        fieldType(if (target != null) target.javaClass else rawClass(type), key)

    override fun get(target: Any, key: String): Any? = propertyValues(target)[key]

    override fun set(target: Any, key: String, value: Any?): Any {
        @Suppress("UNCHECKED_CAST")
        val map = LinkedHashMap(Coercion.mapper.convertValue(target, Map::class.java) as Map<String, Any?>)
        map.putAll(propertyValues(target))
        map[key] = value
        return Coercion.mapper.convertValue(map, target.javaClass)
    }

    private fun hierarchy(cls: Class<*>?): Sequence<Class<*>> =
        generateSequence(cls) { it.superclass }.takeWhile { it != Any::class.java }

    private fun fieldType(cls: Class<*>?, name: String): Type? =
        hierarchy(cls).firstNotNullOfOrNull { c -> c.declaredFields.firstOrNull { it.name == name }?.genericType }

    /** Raw (typed) field values so nested enums / dates keep their types instead of Jackson's JSON forms. */
    private fun propertyValues(target: Any): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        for (c in hierarchy(target.javaClass)) {
            for (f in c.declaredFields) {
                if (java.lang.reflect.Modifier.isStatic(f.modifiers) || f.isSynthetic) continue
                if (f.trySetAccessible()) out.putIfAbsent(f.name, f.get(target))
            }
        }
        return out
    }
}
