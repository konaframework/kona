package io.konaframework.cadence.seeding

import io.konaframework.core.Container

/**
 * Fills the database with data. Override [run]; use [call] to run other seeders from a root
 * seeder (conventionally `database.seeders.DatabaseSeeder`, the class `grind db:seed` runs).
 *
 * Seeders passed to [call] by class are built by the [container] (or a fresh one when none is
 * set), so their constructors can take services.
 */
abstract class Seeder {
    var container: Container? = null

    private val calledList = mutableListOf<String>()

    /** Simple class names of seeders run through [call], in order (nested calls included). */
    val called: List<String> get() = calledList.toList()

    abstract fun run()

    /** Runs each seeder in order. */
    fun call(vararg seeders: Seeder) {
        seeders.forEach { seeder ->
            seeder.container = container
            seeder.run()
            calledList += seeder.javaClass.simpleName
            calledList += seeder.called
        }
    }

    /** Builds each seeder through the container and runs it. */
    fun call(vararg seeders: Class<out Seeder>) {
        call(*seeders.map { instantiate(it, container) }.toTypedArray())
    }

    companion object {
        /** Resolves [type] through [container] (a fresh [Container] when null) and hands it that container. */
        @JvmStatic
        fun <S : Seeder> instantiate(type: Class<S>, container: Container?): S {
            val resolver = container ?: Container()
            return resolver.resolve(type).also { it.container = resolver }
        }
    }
}
