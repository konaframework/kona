package io.konaframework.queue

/** Dispatches jobs sequentially: each job runs only after the previous succeeds. */
class PendingChain(
    private val bus: Bus,
    private val jobs: List<Job>,
) {
    @JvmOverloads
    fun dispatch(connection: String? = null, queue: String? = null, delaySeconds: Long = 0): String {
        require(jobs.isNotEmpty()) { "Cannot dispatch an empty chain" }
        return bus.dispatch(ChainedJob(jobs.toMutableList()), connection, queue, delaySeconds)
    }
}

/**
 * Runs steps in order. After each step, re-dispatches itself when more remain
 * so the same in-process factory instance continues the chain.
 */
class ChainedJob(
    private val remaining: MutableList<Job>,
) : Job, ShouldQueue {
    override fun handle() {
        require(remaining.isNotEmpty()) { "ChainedJob has no remaining steps" }
        val next = remaining.removeAt(0)
        JobPipeline.run(next)
        if (remaining.isEmpty()) {
            return
        }
        val bus = WorkerContext.bus()
            ?: throw IllegalStateException("No Bus bound in WorkerContext for chain continuation")
        bus.dispatch(this, WorkerContext.connection(), WorkerContext.queue())
    }
}
