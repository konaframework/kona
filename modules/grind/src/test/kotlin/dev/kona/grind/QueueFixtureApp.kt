package dev.kona.grind

import dev.kona.core.Application
import dev.kona.core.Kona
import dev.kona.queue.Bus
import dev.kona.queue.FailedJobProvider
import dev.kona.queue.Job
import dev.kona.queue.JobContract
import dev.kona.queue.JobRegistry
import dev.kona.queue.QueuePayload
import dev.kona.queue.QueueServiceProvider
import dev.kona.queue.ShouldQueue
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
