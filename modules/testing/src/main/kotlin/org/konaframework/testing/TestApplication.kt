package org.konaframework.testing

import org.konaframework.core.Application
import org.konaframework.core.Kona
import org.konaframework.core.ServiceProvider
import java.nio.file.Path

/**
 * Boots a Kona application for tests.
 */
object TestApplication {
    @JvmStatic
    fun create(base: Path, vararg providers: ServiceProvider): Application =
        Kona.create(arrayOf("--base=$base")).providers(*providers).boot()
}
