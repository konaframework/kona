package org.konaframework.support.collection

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

    fun <R> flatMap(transform: (T) -> Iterable<R>): Collection<R> =
        Collection(items.flatMap(transform))

    fun <R> reduce(initial: R, operation: (R, T) -> R): R =
        items.fold(initial, operation)

    fun reduce(operation: (T, T) -> T): T {
        if (items.isEmpty()) {
            throw IllegalStateException("Cannot reduce an empty collection")
        }
        return items.reduce(operation)
    }

    fun chunk(size: Int): Collection<Collection<T>> {
        if (size < 1) {
            throw IllegalArgumentException("Chunk size must be at least 1")
        }
        return Collection(items.chunked(size).map { Collection(it) })
    }

    fun sum(): Double {
        var total = 0.0
        for (item in items) {
            total += when (item) {
                is Number -> item.toDouble()
                else -> 0.0
            }
        }
        return total
    }

    fun avg(): Double? {
        if (items.isEmpty()) {
            return null
        }
        return sum() / items.size
    }

    /** Runs [block] when [condition] is true; always returns this collection. */
    fun `when`(condition: Boolean, block: (Collection<T>) -> Unit): Collection<T> {
        if (condition) {
            block(this)
        }
        return this
    }

    /** Runs [block] when [condition] is false; always returns this collection. */
    fun unless(condition: Boolean, block: (Collection<T>) -> Unit): Collection<T> {
        if (!condition) {
            block(this)
        }
        return this
    }

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

    @JvmOverloads
    fun implode(separator: String, transform: (T) -> CharSequence = { it.toString() }): String =
        items.joinToString(separator, transform = transform)

    /** Alias for [implode]. */
    @JvmOverloads
    fun join(separator: String, transform: (T) -> CharSequence = { it.toString() }): String =
        implode(separator, transform)

    fun toList(): List<T> = items.toList()

    fun reject(predicate: (T) -> Boolean): Collection<T> =
        Collection(items.filterNot(predicate))

    fun first(predicate: (T) -> Boolean): T? = items.firstOrNull(predicate)

    fun last(predicate: (T) -> Boolean): T? = items.lastOrNull(predicate)

    /** Filter map-like items where [key] equals [value]. */
    fun where(key: String, value: Any?): Collection<T> =
        filter { item -> mapValue(item, key) == value }

    fun firstWhere(key: String, value: Any?): T? = where(key, value).first()

    fun merge(other: Iterable<T>): Collection<T> =
        Collection(items + other)

    fun concat(other: Iterable<T>): Collection<T> = merge(other)

    /** One-level flatten when items are iterables or nested collections. */
    @Suppress("UNCHECKED_CAST")
    fun collapse(): Collection<Any?> {
        val out = mutableListOf<Any?>()
        for (item in items) {
            when (item) {
                is Collection<*> -> out.addAll(item.toList())
                is Iterable<*> -> out.addAll(item)
                is Array<*> -> out.addAll(item)
                else -> out.add(item)
            }
        }
        return Collection(out)
    }

    /** Recursive flatten of nested iterables. */
    fun flatten(): Collection<Any?> = Collection(flattenItems(items))

    fun partition(predicate: (T) -> Boolean): List<Collection<T>> {
        val pass = mutableListOf<T>()
        val fail = mutableListOf<T>()
        for (item in items) {
            if (predicate(item)) pass.add(item) else fail.add(item)
        }
        return listOf(Collection(pass), Collection(fail))
    }

    fun <K> keyBy(keySelector: (T) -> K): Map<K, T> {
        val out = linkedMapOf<K, T>()
        for (item in items) {
            out[keySelector(item)] = item
        }
        return out
    }

    /** Pluck values keyed by another map field. */
    fun pluck(valueKey: String, keyedBy: String): Map<Any?, Any?> {
        val out = linkedMapOf<Any?, Any?>()
        for (item in items) {
            val key = mapValue(item, keyedBy) ?: continue
            out[key] = mapValue(item, valueKey)
        }
        return out
    }

    @Suppress("UNCHECKED_CAST")
    fun min(): T? {
        if (items.isEmpty()) return null
        return (items as List<Comparable<Any>>).minOrNull() as T?
    }

    @Suppress("UNCHECKED_CAST")
    fun max(): T? {
        if (items.isEmpty()) return null
        return (items as List<Comparable<Any>>).maxOrNull() as T?
    }

    fun every(predicate: (T) -> Boolean): Boolean = items.all(predicate)

    fun pop(): T? = if (items.isEmpty()) null else items.removeAt(items.lastIndex)

    fun shift(): T? = if (items.isEmpty()) null else items.removeAt(0)

    fun prepend(item: T): Collection<T> {
        items.add(0, item)
        return this
    }

    @JvmOverloads
    fun slice(start: Int, length: Int? = null): Collection<T> {
        if (items.isEmpty()) return empty()
        val from = start.coerceAtLeast(0).coerceAtMost(items.size)
        val to =
            if (length == null) {
                items.size
            } else {
                (from + length.coerceAtLeast(0)).coerceAtMost(items.size)
            }
        return Collection(items.subList(from, to).toList())
    }

    fun tap(callback: (Collection<T>) -> Unit): Collection<T> {
        callback(this)
        return this
    }

    fun whenEmpty(block: (Collection<T>) -> Unit): Collection<T> = `when`(items.isEmpty(), block)

    fun whenNotEmpty(block: (Collection<T>) -> Unit): Collection<T> = `when`(items.isNotEmpty(), block)

    fun forget(index: Int): Collection<T> {
        if (index in items.indices) {
            items.removeAt(index)
        }
        return this
    }

    fun search(item: T): Int? {
        val idx = items.indexOf(item)
        return if (idx < 0) null else idx
    }

    fun sole(): T {
        if (items.isEmpty()) {
            throw NoSuchElementException("Item not found.")
        }
        if (items.size > 1) {
            throw IllegalStateException("Multiple items found.")
        }
        return items[0]
    }

    fun sole(predicate: (T) -> Boolean): T = filter(predicate).sole()

    @JvmOverloads
    fun nth(step: Int, offset: Int = 0): Collection<T> {
        require(step > 0) { "Step must be positive" }
        val out = mutableListOf<T>()
        var i = offset.coerceAtLeast(0)
        while (i < items.size) {
            out.add(items[i])
            i += step
        }
        return Collection(out)
    }

    fun sum(selector: (T) -> Number): Double {
        var total = 0.0
        for (item in items) {
            total += selector(item).toDouble()
        }
        return total
    }

    fun diff(other: Iterable<T>): Collection<T> {
        val set = other.toSet()
        return Collection(items.filter { it !in set })
    }

    fun intersect(other: Iterable<T>): Collection<T> {
        val set = other.toSet()
        return Collection(items.filter { it in set })
    }

    fun <K, V> mapWithKeys(transform: (T) -> Pair<K, V>): Map<K, V> {
        val out = linkedMapOf<K, V>()
        for (item in items) {
            val (k, v) = transform(item)
            out[k] = v
        }
        return out
    }

    fun <K> countBy(keySelector: (T) -> K): Map<K, Int> {
        val out = linkedMapOf<K, Int>()
        for (item in items) {
            val key = keySelector(item)
            out[key] = (out[key] ?: 0) + 1
        }
        return out
    }

    fun pad(size: Int, value: T): Collection<T> {
        if (size <= items.size) {
            return Collection(items.take(size))
        }
        val out = items.toMutableList()
        while (out.size < size) {
            out.add(value)
        }
        return Collection(out)
    }

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

        @JvmStatic
        fun <T> times(count: Int, factory: (Int) -> T): Collection<T> {
            require(count >= 0) { "Count must be non-negative" }
            return Collection((0 until count).map(factory))
        }

        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        fun <T> wrap(value: Any?): Collection<T> =
            when (value) {
                null -> empty()
                is Collection<*> -> value as Collection<T>
                is Iterable<*> -> of(value as Iterable<T>)
                is Array<*> -> of(value.toList() as List<T>)
                else -> make(value as T)
            }

        private fun mapValue(item: Any?, key: String): Any? =
            when (item) {
                is Map<*, *> -> item[key]
                else -> null
            }

        private fun flattenItems(source: Iterable<*>): List<Any?> {
            val out = mutableListOf<Any?>()
            for (item in source) {
                when (item) {
                    is Collection<*> -> out.addAll(flattenItems(item.toList()))
                    is Iterable<*> -> out.addAll(flattenItems(item))
                    is Array<*> -> out.addAll(flattenItems(item.toList()))
                    else -> out.add(item)
                }
            }
            return out
        }
    }
}

/** Java-friendly alias matching Laravel's `collect()`. */
@JvmName("collectIterable")
fun <T> collect(items: Iterable<T>): Collection<T> = Collection.of(items)

fun <T> collect(vararg items: T): Collection<T> = Collection.make(*items)
