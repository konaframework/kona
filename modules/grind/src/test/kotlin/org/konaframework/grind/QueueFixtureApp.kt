package org.konaframework.grind

import org.konaframework.core.Application
import org.konaframework.core.Kona
import org.konaframework.queue.Bus
import org.konaframework.queue.FailedJobProvider
import org.konaframework.queue.Job
import org.konaframework.queue.JobContract
import org.konaframework.queue.JobRegistry
import org.konaframework.queue.QueuePayload
import org.konaframework.queue.QueueServiceProvider
import org.konaframework.queue.ShouldQueue
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
