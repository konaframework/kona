package dev.kona.queue

/** Runs a job through its [HasMiddleware] stack (outermost first). */
object JobPipeline {
    fun run(job: Job) {
        val middleware = if (job is HasMiddleware) job.middleware() else emptyList()
        invoke(job, middleware, 0)
    }

    private fun invoke(job: Job, middleware: List<JobMiddleware>, index: Int) {
        if (index >= middleware.size) {
            job.handle()
            return
        }
        middleware[index].handle(job) { invoke(job, middleware, index + 1) }
    }
}
