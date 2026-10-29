package database.seeders

import app.models.Project
import app.models.Task
import org.konaframework.cadence.seeding.Seeder

class TaskSeeder : Seeder() {
    override fun run() {
        val desk = Project.query().where("name", "Desk").first()
        val lab = Project.query().where("name", "Lab").first()
        listOf(
            mapOf("name" to "Wire combobox", "status" to "open", "project_id" to desk?.getKey()),
            mapOf("name" to "Seed sample data", "status" to "done", "project_id" to desk?.getKey()),
            mapOf("name" to "Calibrate sensors", "status" to "open", "project_id" to lab?.getKey()),
        ).forEach { row ->
            Task().forceFill(row).save()
        }
    }
}
