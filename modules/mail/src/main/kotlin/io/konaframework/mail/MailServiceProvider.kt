package io.konaframework.mail

import io.konaframework.core.Application
import io.konaframework.core.ServiceProvider

class MailServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        app.container.singleton(MailManager::class.java) { MailManager(app.config) }
        app.container.singleton(Mailer::class.java) { app.resolve(MailManager::class.java).mailer() }
    }
}
