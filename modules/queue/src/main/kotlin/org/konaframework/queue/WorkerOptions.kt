package org.konaframework.queue

/** Runtime options for [Worker] (mirrors grind `queue:work` flags). */
data class WorkerOptions @JvmOverloads constructor(
    val maxJobs: Int = Int.MAX_VALUE,
    val once: Boolean = false,
    val stopWhenEmpty: Boolean = true,
    val timeoutSeconds: Long = 60,
    /** Max used heap in MB before the worker stops; `0` disables the check. */
    val memoryMb: Int = 0,
    val maxTries: Int? = null,
    val backoffSeconds: Long? = null,
    val sleepSeconds: Long = 3,
    /** When not stop-when-empty, stop after this many consecutive empty polls. */
    val maxIdleCycles: Int = Int.MAX_VALUE,
)
