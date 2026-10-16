package io.konaframework.schedule

import io.konaframework.core.Application
import io.konaframework.core.ServiceProvider

class ScheduleServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        app.container.singleton(Schedule::class.java) {
            Schedule(app.basePath)
        }
    }
}
