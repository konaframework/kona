package org.konaframework.firefly.tables

import org.konaframework.cadence.Cadence
import org.konaframework.cadence.ModelBuilder
import java.util.function.Supplier

/**
 * A constraint every Cadence record store applies while it is active on the thread: tables,
 * relation tables, select options, record lookups and saves all go through the stores, so a scope
 * set here (multi-tenancy) reaches them without each caller asking.
 */
interface RecordScope {
    /** Adds the scope's constraints to [query], a query over [model]. */
    fun constrain(model: Class<*>, query: ModelBuilder<*>)

    /** Runs [save] for a [record] that is stored for the first time, stamping the scope on it around the save. */
    fun create(record: Cadence<*>, save: Runnable)
}

/** The [RecordScope] active on this thread. */
object RecordScopes {
    private val active = ThreadLocal<RecordScope?>()

    @JvmStatic
    fun current(): RecordScope? = active.get()

    /** Runs [block] with [scope] active (null turns scoping off) and restores the previous scope. */
    @JvmStatic
    fun <R> use(scope: RecordScope?, block: Supplier<R>): R {
        val previous = active.get()
        active.set(scope)
        try {
            return block.get()
        } finally {
            active.set(previous)
        }
    }
}
