package dev.kona.extract

/**
 * Collection helpers for lists of [Extract] models (Laravel Eloquent Collection shape).
 */
class ExtractCollection<T : Extract<T>>(
    private val items: MutableList<T>,
) : Iterable<T> {
    constructor(items: Iterable<T> = emptyList()) : this(items.toMutableList())

    fun all(): List<T> = items.toList()

    fun count(): Int = items.size

    fun isEmpty(): Boolean = items.isEmpty()

    fun first(): T? = items.firstOrNull()

    fun last(): T? = items.lastOrNull()

    fun pluck(attribute: String): List<Any?> = items.map { it.getAttribute(attribute) }

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

    fun load(vararg relations: String): ExtractCollection<T> {
        if (items.isNotEmpty()) {
            dev.kona.extract.relations.EagerLoader.load(items, *relations)
        }
        return this
    }

    fun each(action: (T) -> Unit): ExtractCollection<T> {
        items.forEach(action)
        return this
    }

    fun filter(predicate: (T) -> Boolean): ExtractCollection<T> =
        ExtractCollection(items.filter(predicate))

    fun toList(): List<T> = items.toList()

    override fun iterator(): Iterator<T> = items.iterator()

    companion object {
        @JvmStatic
        fun <T : Extract<T>> of(items: Iterable<T>): ExtractCollection<T> = ExtractCollection(items)

        @JvmStatic
        fun <T : Extract<T>> make(vararg items: T): ExtractCollection<T> = ExtractCollection(items.toList())
    }
}
