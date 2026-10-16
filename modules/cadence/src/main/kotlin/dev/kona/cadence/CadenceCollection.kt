package dev.kona.cadence

import dev.kona.cadence.relations.EagerLoader
import dev.kona.cadence.relations.Relation

/**
 * Collection helpers for lists of [Cadence] models (Laravel Eloquent Collection shape).
 */
class CadenceCollection<T : Cadence<T>>(
    private val items: MutableList<T>,
) : Iterable<T> {
    constructor(items: Iterable<T> = emptyList()) : this(items.toMutableList())

    fun all(): List<T> = items.toList()

    fun count(): Int = items.size

    fun isEmpty(): Boolean = items.isEmpty()

    fun first(): T? = items.firstOrNull()

    fun last(): T? = items.lastOrNull()

    fun find(id: Any?): T? =
        items.firstOrNull { Relation.normalizeKey(it.getKey()) == Relation.normalizeKey(id) }

    fun pluck(attribute: String): List<Any?> = items.map { it.getAttribute(attribute) }

    /** Keyed pluck: map of [key] attribute → [attribute] value. */
    fun pluck(attribute: String, key: String): Map<Any?, Any?> {
        val result = linkedMapOf<Any?, Any?>()
        for (item in items) {
            result[item.getAttribute(key)] = item.getAttribute(attribute)
        }
        return result
    }

    fun modelKeys(): List<Any?> = items.mapNotNull { it.getKey() }

    fun toArray(): List<Map<String, Any?>> = items.map { it.toArray() }

    fun toMapKeyedById(): Map<Any, T> {
        val result = linkedMapOf<Any, T>()
        for (item in items) {
            val key = item.getKey() ?: continue
            result[key] = item
        }
        return result
    }

    fun load(vararg relations: String): CadenceCollection<T> {
        if (items.isNotEmpty()) {
            EagerLoader.load(items, *relations)
        }
        return this
    }

    /** Loads only relations that are missing on at least one model in the collection. */
    fun loadMissing(vararg relations: String): CadenceCollection<T> {
        if (items.isEmpty() || relations.isEmpty()) {
            return this
        }
        val missing = relations.filter { name ->
            val top = name.substringBefore('.')
            items.any { !it.relationLoaded(top) }
        }
        if (missing.isNotEmpty()) {
            EagerLoader.load(items, *missing.toTypedArray())
        }
        return this
    }

    fun each(action: (T) -> Unit): CadenceCollection<T> {
        items.forEach(action)
        return this
    }

    fun filter(predicate: (T) -> Boolean): CadenceCollection<T> =
        CadenceCollection(items.filter(predicate))

    fun toList(): List<T> = items.toList()

    override fun iterator(): Iterator<T> = items.iterator()

    companion object {
        @JvmStatic
        fun <T : Cadence<T>> of(items: Iterable<T>): CadenceCollection<T> = CadenceCollection(items)

        @JvmStatic
        fun <T : Cadence<T>> make(vararg items: T): CadenceCollection<T> = CadenceCollection(items.toList())
    }
}
