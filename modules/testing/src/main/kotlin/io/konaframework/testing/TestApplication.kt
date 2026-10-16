package io.konaframework.testing

import io.konaframework.core.Application
import io.konaframework.core.Kona
import io.konaframework.core.ServiceProvider
import java.nio.file.Path

/**
 * Boots a Kona application for tests.
 */
object TestApplication {
    @JvmStatic
    fun create(base: Path, vararg providers: ServiceProvider): Application =
        Kona.create(arrayOf("--base=$base")).providers(*providers).boot()
}
