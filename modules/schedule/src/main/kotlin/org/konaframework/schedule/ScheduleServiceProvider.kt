package org.konaframework.schedule

import org.konaframework.core.Application
import org.konaframework.core.ServiceProvider

class ScheduleServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        app.container.singleton(Schedule::class.java) {
            Schedule(app.basePath)
        }
    }
}
