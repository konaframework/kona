package io.konaframework.grind

import io.konaframework.core.Application
import io.konaframework.core.Kona
import io.konaframework.queue.Bus
import io.konaframework.queue.FailedJobProvider
import io.konaframework.queue.Job
import io.konaframework.queue.JobContract
import io.konaframework.queue.JobRegistry
import io.konaframework.queue.QueuePayload
import io.konaframework.queue.QueueServiceProvider
import io.konaframework.queue.ShouldQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object QueueFixtureApp {
    val counter = AtomicInteger()
    val seedFailed = AtomicBoolean(true)

    @JvmStatic
    fun create(args: Array<String>): Application {
        counter.set(0)
        JobRegistry.clear()
        val app = Kona.create(args)
            .configure {
                it.set("queue.default", "array")
                it.set("queue.connections.array.driver", "array")
            }
            .providers(QueueServiceProvider())
            .boot()
        app.resolve(Bus::class.java).dispatch(
            object : Job, ShouldQueue, JobContract {
                override fun handle() {
                    counter.incrementAndGet()
                }

                override fun tries(): Int = 3

                override fun backoffSeconds(): Long = 0

                override fun displayName(): String = "FixtureJob"
            },
        )
        if (seedFailed.get()) {
            app.resolve(FailedJobProvider::class.java).log(
                "array",
                "default",
                QueuePayload("fail-1", "BoomJob", 1, 0, 1, "boom"),
                RuntimeException("boom"),
            )
            JobRegistry.register("boom") { Job { } }
        }
        return app
    }
}

object NoQueueApp {
    @JvmStatic
    fun create(args: Array<String>): Application = Kona.create(args).boot()
}

object FailedOnlyApp {
    @JvmStatic
    fun create(args: Array<String>): Application {
        val app = Kona.create(args).boot()
        app.container.instance(FailedJobProvider::class.java, FailedJobProvider())
        return app
    }
}
