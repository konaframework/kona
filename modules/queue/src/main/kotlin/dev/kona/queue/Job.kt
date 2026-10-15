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

/** Jobs that expose middleware wrapping [Job.handle]. */
interface HasMiddleware {
    fun middleware(): List<JobMiddleware>
}

/** Middleware around a job's [Job.handle] call. */
fun interface JobMiddleware {
    fun handle(job: Job, next: Runnable)
}

/** Only one instance with the same [uniqueId] may run at a time. */
interface ShouldBeUnique {
    fun uniqueId(): String

    fun uniqueForSeconds(): Long = 3600
}

/** Thrown to release back onto the queue without counting a failure. */
class ReleaseJobException(
    val delaySeconds: Long = 0,
    message: String = "Job released",
) : RuntimeException(message)
