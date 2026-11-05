package org.konaframework.inertia

import java.util.concurrent.Callable
import java.util.function.Supplier

/**
 * Marker for props skipped on the first (non-partial) visit — loaded only when
 * a partial reload names them (`Inertia.optional` / deprecated `lazy`).
 */
interface IgnoreFirstLoad

/** Props deferred to a follow-up partial reload, grouped for parallel fetches. */
interface Deferrable {
    fun group(): String

    fun shouldDefer(): Boolean = true
}

/** Props the client should merge instead of replace on partial reloads. */
interface Mergeable {
    fun shouldMerge(): Boolean

    fun shouldDeepMerge(): Boolean

    fun appendsAtRoot(): Boolean

    fun prependsAtRoot(): Boolean

    fun appendsAtPaths(): List<String> = emptyList()

    fun prependsAtPaths(): List<String> = emptyList()

    fun matchesOn(): List<String> = emptyList()
}

/** Evaluates to a JSON-serializable value when the page is built. */
fun interface ResolvableProp {
    fun resolve(): Any?
}

internal fun resolvePropValue(value: Any?): Any? =
    when (value) {
        null -> null
        is ResolvableProp -> resolvePropValue(value.resolve())
        is Function0<*> -> resolvePropValue(value.invoke())
        is Supplier<*> -> resolvePropValue(value.get())
        is Callable<*> -> resolvePropValue(value.call())
        else -> value
    }

/**
 * Lazy / optional prop — omitted on full visits; resolved on partial reloads that request it.
 */
class OptionalProp(
    private val callback: () -> Any?,
) : IgnoreFirstLoad, ResolvableProp {
    override fun resolve(): Any? = resolvePropValue(callback())
}

/** @deprecated Prefer [OptionalProp] / [Inertia.optional]. */
typealias LazyProp = OptionalProp

/**
 * Always included, even when a partial reload uses `only` / `except`.
 */
class AlwaysProp(
    private val value: Any?,
) : ResolvableProp {
    override fun resolve(): Any? = resolvePropValue(value)
}

/**
 * Deferred prop — skipped on the first visit and listed under `deferredProps`.
 */
class DeferProp(
    private val callback: () -> Any?,
    private val groupName: String = "default",
) : Deferrable, Mergeable, ResolvableProp {
    private var merge: Boolean = false
    private var deepMerge: Boolean = false
    private var append: Boolean = true

    override fun group(): String = groupName

    override fun resolve(): Any? = resolvePropValue(callback())

    fun merge(): DeferProp {
        merge = true
        return this
    }

    fun deepMerge(): DeferProp {
        deepMerge = true
        merge = true
        return this
    }

    fun prepend(): DeferProp {
        append = false
        merge = true
        return this
    }

    override fun shouldMerge(): Boolean = merge

    override fun shouldDeepMerge(): Boolean = deepMerge

    override fun appendsAtRoot(): Boolean = shouldMerge() && append && !shouldDeepMerge()

    override fun prependsAtRoot(): Boolean = shouldMerge() && !append && !shouldDeepMerge()
}

/**
 * Mergeable prop — client appends / deep-merges on partial reloads.
 */
class MergeProp(
    private val value: Any?,
) : Mergeable, ResolvableProp {
    private var merge: Boolean = true
    private var deepMerge: Boolean = false
    private var append: Boolean = true
    private val matchOnKeys = mutableListOf<String>()

    override fun resolve(): Any? = resolvePropValue(value)

    fun merge(): MergeProp {
        merge = true
        return this
    }

    fun deepMerge(): MergeProp {
        this.deepMerge = true
        merge = true
        return this
    }

    fun prepend(): MergeProp {
        append = false
        return this
    }

    fun matchOn(vararg keys: String): MergeProp {
        matchOnKeys.addAll(keys)
        return this
    }

    override fun shouldMerge(): Boolean = merge

    override fun shouldDeepMerge(): Boolean = deepMerge

    override fun appendsAtRoot(): Boolean = append && !deepMerge

    override fun prependsAtRoot(): Boolean = !append && !deepMerge

    override fun matchesOn(): List<String> = matchOnKeys.toList()
}
