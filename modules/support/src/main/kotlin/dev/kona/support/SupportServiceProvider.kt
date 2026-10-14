package dev.kona.support

import dev.kona.core.Application
import dev.kona.core.ServiceProvider
import dev.kona.support.lang.Lang
import dev.kona.support.lang.Translator

/**
 * Registers shared support services (translator).
 */
class SupportServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        app.container.singleton(Translator::class.java) { _ ->
            val translator = Translator()
            Lang.setTranslator(translator)
            translator
        }
    }
}
