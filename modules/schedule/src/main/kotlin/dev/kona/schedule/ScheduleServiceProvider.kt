package dev.kona.schedule

import dev.kona.core.Application
import dev.kona.core.ServiceProvider

class ScheduleServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        app.container.singleton(Schedule::class.java) { Schedule() }
    }
}
