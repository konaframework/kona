package dev.kona.testing

import dev.kona.core.Application
import dev.kona.core.Kona
import dev.kona.core.ServiceProvider
import java.nio.file.Path

/**
 * Boots a Kona application for tests.
 */
object TestApplication {
    @JvmStatic
    fun create(base: Path, vararg providers: ServiceProvider): Application =
        Kona.create(arrayOf("--base=$base")).providers(*providers).boot()
}
