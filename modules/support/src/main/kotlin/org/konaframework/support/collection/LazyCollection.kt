package org.konaframework.support.collection

/**
 * A collection that runs `map`, `filter`, and the other chainable steps when something
 * reads a value (`first`, `count`, iteration, or [collect]).
 */
class LazyCollection<T> private constructor(
    private val sequence: Sequence<T>,
) : Iterable<T> {
    constructor(items: Iterable<T>) : this(items.asSequence())

    fun <R> map(transform: (T) -> R): LazyCollection<R> = LazyCollection(sequence.map(transform))

    fun filter(predicate: (T) -> Boolean): LazyCollection<T> =
        LazyCollection(sequence.filter(predicate))

    fun reject(predicate: (T) -> Boolean): LazyCollection<T> =
        LazyCollection(sequence.filter { !predicate(it) })

    fun take(n: Int): LazyCollection<T> {
        val limit = if (n < 0) 0 else n
        return LazyCollection(sequence.take(limit))
    }

    fun skip(n: Int): LazyCollection<T> {
        val count = if (n < 0) 0 else n
        return LazyCollection(sequence.drop(count))
    }

    fun unique(): LazyCollection<T> = LazyCollection(sequence.distinct())

    fun <R> flatMap(transform: (T) -> Iterable<R>): LazyCollection<R> =
        LazyCollection(sequence.flatMap { transform(it).asSequence() })

    fun tap(callback: (T) -> Unit): LazyCollection<T> = LazyCollection(sequence.onEach(callback))

    /** Reads every item, running [action] for each, and returns this collection. */
    fun each(action: (T) -> Unit): LazyCollection<T> {
        sequence.forEach(action)
        return this
    }

    fun first(): T? = sequence.firstOrNull()

    fun first(predicate: (T) -> Boolean): T? = sequence.firstOrNull(predicate)

    fun count(): Int = sequence.count()

    fun isEmpty(): Boolean = sequence.none()

    fun all(): List<T> = sequence.toList()

    fun collect(): Collection<T> = Collection.of(sequence.toList())

    fun higher(operation: String): HigherOrderCollectionProxy<T> =
        HigherOrderCollectionProxy(this, operation, true)

    override fun iterator(): Iterator<T> = sequence.iterator()

    companion object {
        @JvmStatic
        fun <T> make(vararg items: T): LazyCollection<T> = LazyCollection(items.asList())

        @JvmStatic
        fun <T> of(items: Iterable<T>): LazyCollection<T> = LazyCollection(items)

        @JvmStatic
        fun <T> empty(): LazyCollection<T> = LazyCollection(emptySequence())
    }
}
