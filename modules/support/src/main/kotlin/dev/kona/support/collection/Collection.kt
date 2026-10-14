package dev.kona.support.collection

/**
 * Fluent collection helpers (Laravel Collection shape) wrapping a list.
 */
class Collection<T> private constructor(
    private val items: MutableList<T>,
) : Iterable<T> {
    constructor(items: Iterable<T> = emptyList()) : this(items.toMutableList())

    fun all(): List<T> = items.toList()

    fun values(): Collection<T> = Collection(items)

    fun count(): Int = items.size

    fun isEmpty(): Boolean = items.isEmpty()

    fun isNotEmpty(): Boolean = items.isNotEmpty()

    fun first(): T? = items.firstOrNull()

    fun last(): T? = items.lastOrNull()

    fun get(index: Int): T? = items.getOrNull(index)

    fun push(item: T): Collection<T> {
        items.add(item)
        return this
    }

    fun put(index: Int, item: T): Collection<T> {
        if (index >= 0 && index < items.size) {
            items[index] = item
        } else {
            items.add(item)
        }
        return this
    }

    fun filter(predicate: (T) -> Boolean): Collection<T> =
        Collection(items.filter(predicate))

    fun <R> map(transform: (T) -> R): Collection<R> =
        Collection(items.map(transform))

    fun each(action: (T) -> Unit): Collection<T> {
        items.forEach(action)
        return this
    }

    fun take(n: Int): Collection<T> =
        Collection(items.take(n.coerceAtLeast(0)))

    fun skip(n: Int): Collection<T> =
        Collection(items.drop(n.coerceAtLeast(0)))

    fun reverse(): Collection<T> =
        Collection(items.asReversed().toList())

    fun unique(): Collection<T> =
        Collection(items.distinct())

    fun <K> uniqueBy(keySelector: (T) -> K): Collection<T> =
        Collection(items.distinctBy(keySelector))

    @Suppress("UNCHECKED_CAST")
    fun sort(): Collection<T> {
        val comparable = items as List<Comparable<Any>>
        return Collection(comparable.sorted() as List<T>)
    }

    fun <R : Comparable<R>> sortBy(selector: (T) -> R): Collection<T> =
        Collection(items.sortedBy(selector))

    fun <R : Comparable<R>> sortByDesc(selector: (T) -> R): Collection<T> =
        Collection(items.sortedByDescending(selector))

    fun contains(item: T): Boolean = items.contains(item)

    fun contains(predicate: (T) -> Boolean): Boolean = items.any(predicate)

    fun pluck(key: String): Collection<Any?> =
        Collection(
            items.map { item ->
                when (item) {
                    is Map<*, *> -> item[key]
                    else -> null
                }
            },
        )

    fun <K> groupBy(keySelector: (T) -> K): Map<K, Collection<T>> {
        val grouped = linkedMapOf<K, MutableList<T>>()
        for (item in items) {
            val key = keySelector(item)
            grouped.getOrPut(key) { mutableListOf() }.add(item)
        }
        return grouped.mapValues { Collection(it.value) }
    }

    fun implode(separator: String, transform: (T) -> CharSequence = { it.toString() }): String =
        items.joinToString(separator, transform = transform)

    fun toList(): List<T> = items.toList()

    override fun iterator(): Iterator<T> = items.iterator()

    override fun equals(other: Any?): Boolean =
        other is Collection<*> && other.items == items

    override fun hashCode(): Int = items.hashCode()

    override fun toString(): String = "Collection$items"

    companion object {
        @JvmStatic
        fun <T> make(vararg items: T): Collection<T> = Collection(items.toList())

        @JvmStatic
        fun <T> of(items: Iterable<T>): Collection<T> = Collection(items)

        @JvmStatic
        fun <T> empty(): Collection<T> = Collection()
    }
}

/** Java-friendly alias matching Laravel's `collect()`. */
@JvmName("collectIterable")
fun <T> collect(items: Iterable<T>): Collection<T> = Collection.of(items)

fun <T> collect(vararg items: T): Collection<T> = Collection.make(*items)
