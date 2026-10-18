package org.konaframework.brew.meta

import org.konaframework.brew.Async
import org.konaframework.brew.Component
import org.konaframework.brew.Computed
import org.konaframework.brew.Json
import org.konaframework.brew.Locked
import org.konaframework.brew.Renderless
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Type
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.javaSetter

/** One piece of public component state. */
class PropertyMeta internal constructor(
    val name: String,
    val type: Class<*>,
    val genericType: Type,
    val nullable: Boolean,
    /** Read-only (`val`) state: hydrated from the snapshot but never writable from the browser. */
    val readOnly: Boolean,
    val annotations: List<Annotation>,
    private val field: Field,
) {
    val locked: Boolean = readOnly || annotations.any { it is Locked }

    fun get(target: Any): Any? = field.get(target)

    fun set(target: Any, value: Any?) {
        field.set(target, value)
    }

    fun <A : Annotation> annotation(type: Class<A>): A? = annotations.firstOrNull { type.isInstance(it) }?.let(type::cast)
}

class ComputedMeta internal constructor(val name: String, val method: Method, val attribute: Computed)

/**
 * Per-class reflection metadata, computed once and cached in a [ClassValue] (no per-request scanning).
 */
class ComponentMeta private constructor(val type: Class<*>) {
    val properties: Map<String, PropertyMeta>
    val actions: Map<String, List<Method>>
    val computed: Map<String, ComputedMeta>
    val renderless: Set<String>
    val async: Set<String>
    val mixins: List<String>
    private val methodsByName: Map<String, List<Method>>

    init {
        val chain = generateSequence(type) { it.superclass }
            .takeWhile { it != Component::class.java }
            .toList()
            .reversed()
        properties = collectProperties(chain)
        val publicMethods = type.methods.filter { m ->
            !Modifier.isStatic(m.modifiers) && !m.isSynthetic && !m.declaringClass.isAssignableFrom(Component::class.java)
        }
        methodsByName = publicMethods.groupBy { it.name }
        mixins = collectMixins(type)
        computed = publicMethods
            .filter { it.parameterCount == 0 && it.isAnnotationPresent(Computed::class.java) }
            .associate { it.name to ComputedMeta(it.name, it, it.getAnnotation(Computed::class.java)) }
        val accessorNames = accessorNames(chain, properties.keys)
        actions = publicMethods
            .filter { it.name !in accessorNames && !isLifecycle(it.name) && it.name !in NON_ACTIONS && !DATA_CLASS.matches(it.name) }
            .groupBy { it.name }
        renderless = actions.filterValues { ms ->
            ms.any {
                it.isAnnotationPresent(Renderless::class.java) || it.isAnnotationPresent(Json::class.java)
            }
        }.keys
        async = actions.filterValues { ms -> ms.any { it.isAnnotationPresent(Async::class.java) } }.keys
    }

    /** Hook method by exact name (lifecycle / mixin hooks), preferring the most specific declaration. */
    fun hook(name: String): Method? = methodsByName[name]?.maxByOrNull { it.parameterCount }

    /** Lifecycle names that must never be invoked from the browser (Livewire protected methods). */
    fun isLifecycle(name: String): Boolean {
        if (name in LIFECYCLE_EXACT || LIFECYCLE_PREFIXED.containsMatchIn(name)) return true
        return mixins.any { mixin -> MIXIN_HOOKS.any { name == it + mixin } }
    }

    private fun collectProperties(chain: List<Class<*>>): Map<String, PropertyMeta> {
        val out = linkedMapOf<String, PropertyMeta>()
        for (cls in chain) {
            if (cls.isAnnotationPresent(Metadata::class.java)) {
                kotlinProperties(cls, out)
            } else {
                javaFields(cls, out)
            }
        }
        return out
    }

    private fun kotlinProperties(cls: Class<*>, out: MutableMap<String, PropertyMeta>) {
        val byField = cls.kotlin.declaredMemberProperties
            .filter { it.visibility == KVisibility.PUBLIC }
            .mapNotNull { prop -> prop.javaField?.let { it.name to prop } }
            .toMap()
        for (field in cls.declaredFields) {
            if (!isStateField(field)) continue
            val prop = byField[field.name] ?: continue
            field.isAccessible = true
            val getter = prop.javaGetter
            val annotations = field.annotations.toList() + (if (getter == null) emptyList() else getter.annotations.toList())
            out[field.name] = PropertyMeta(
                name = field.name,
                type = field.type,
                genericType = field.genericType,
                nullable = prop.returnType.isMarkedNullable,
                readOnly = prop !is KMutableProperty1<*, *> || prop.setter.visibility != KVisibility.PUBLIC,
                annotations = annotations,
                field = field,
            )
        }
    }

    private fun javaFields(cls: Class<*>, out: MutableMap<String, PropertyMeta>) {
        for (field in cls.declaredFields) {
            if (!isStateField(field) || !Modifier.isPublic(field.modifiers)) continue
            field.isAccessible = true
            out[field.name] = PropertyMeta(
                name = field.name,
                type = field.type,
                genericType = field.genericType,
                nullable = !field.type.isPrimitive,
                readOnly = Modifier.isFinal(field.modifiers),
                annotations = field.annotations.toList(),
                field = field,
            )
        }
    }

    private fun isStateField(field: Field): Boolean {
        val mods = field.modifiers
        return !Modifier.isStatic(mods) && !Modifier.isTransient(mods) && !field.name.contains('$')
    }

    companion object {
        private val CACHE = object : ClassValue<ComponentMeta>() {
            override fun computeValue(type: Class<*>): ComponentMeta = ComponentMeta(type)
        }

        @JvmStatic
        fun of(type: Class<*>): ComponentMeta = CACHE.get(type)

        @JvmStatic
        fun of(component: Any): ComponentMeta = CACHE.get(component.javaClass)

        private val LIFECYCLE_EXACT = setOf(
            "mount", "boot", "booted", "exception", "hydrate", "dehydrate", "updating", "updated",
            "rendering", "rendered", "scriptSrc",
        )
        private val LIFECYCLE_PREFIXED = Regex("^(hydrate|dehydrate|updating|updated)[A-Z]")
        private val MIXIN_HOOKS = listOf(
            "boot", "initialize", "mount", "booted", "hydrate", "dehydrate", "updating", "updated",
            "rendering", "rendered", "exception",
        )
        private val NON_ACTIONS = setOf("equals", "hashCode", "toString", "copy", "render", "template")
        private val DATA_CLASS = Regex("component\\d+")

        /** Kotlin property accessors (state or not) and JavaBean accessors of Java state fields are never actions. */
        private fun accessorNames(chain: List<Class<*>>, props: Collection<String>): Set<String> {
            val out = HashSet<String>()
            for (cls in chain.filter { it.isAnnotationPresent(Metadata::class.java) }) {
                for (prop in cls.kotlin.declaredMemberProperties) {
                    prop.javaGetter?.let { out += it.name }
                    if (prop is KMutableProperty1<*, *>) prop.javaSetter?.let { out += it.name }
                }
            }
            for (p in props) {
                val cap = p.substring(0, 1).uppercase() + p.substring(1)
                out += listOf("get$cap", "set$cap", "is$cap")
            }
            return out
        }

        private fun collectMixins(type: Class<*>): List<String> {
            val seen = linkedSetOf<Class<*>>()
            fun visit(c: Class<*>) {
                for (i in c.interfaces) {
                    if (seen.add(i)) visit(i)
                }
                c.superclass?.let(::visit)
            }
            visit(type)
            return seen.map { it.simpleName }
        }
    }
}
