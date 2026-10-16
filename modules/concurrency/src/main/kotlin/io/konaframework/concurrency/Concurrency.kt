package io.konaframework.concurrency

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.function.Function

/**
 * Virtual-thread friendly concurrency helpers.
 */
object Concurrency {
    @JvmStatic
    fun <T, R> map(items: List<T>, mapper: Function<T, R>): List<R> {
        if (items.isEmpty()) return emptyList()
        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            val futures: List<Future<R>> = items.map { item ->
                executor.submit(Callable { mapper.apply(item) })
            }
            return futures.map { it.get() }
        }
    }

    @JvmStatic
    fun <R> run(tasks: List<Callable<R>>): List<R> {
        if (tasks.isEmpty()) return emptyList()
        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            return tasks.map { executor.submit(it) }.map { it.get() }
        }
    }
}
