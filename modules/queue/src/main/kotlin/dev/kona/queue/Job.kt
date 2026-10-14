package dev.kona.queue

/**
 * A unit of work that can be pushed onto a queue.
 * Implement [ShouldQueue] when dispatching asynchronously; sync jobs run immediately.
 */
fun interface Job {
    fun handle()
}

/** Marker: dispatch via a queue connection instead of running inline. */
interface ShouldQueue

/**
 * Optional job metadata. Defaults are applied in [JobRegistry.payloadFor] when absent.
 */
interface JobContract : Job {
    fun tries(): Int

    fun backoffSeconds(): Long

    fun displayName(): String
}
