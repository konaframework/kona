package database.seeders

import app.models.Note
import org.konaframework.cadence.seeding.Seeder

class NoteSeeder : Seeder() {
    override fun run() {
        listOf(
            mapOf("name" to "Ada", "status" to "open", "due" to "2026-11-01 09:00:00"),
            mapOf("name" to "Ship notes", "status" to "done", "due" to null),
        ).forEach { row ->
            Note().forceFill(row).save()
        }
    }
}
