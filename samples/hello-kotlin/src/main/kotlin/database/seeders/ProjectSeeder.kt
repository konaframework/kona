package database.seeders

import app.models.Project
import org.konaframework.cadence.seeding.Seeder

class ProjectSeeder : Seeder() {
    override fun run() {
        listOf(
            mapOf("name" to "Desk", "active" to true),
            mapOf("name" to "Lab", "active" to true),
            mapOf("name" to "Archive", "active" to false),
        ).forEach { row ->
            Project().forceFill(row).save()
        }
    }
}
