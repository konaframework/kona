package database.seeders

import org.konaframework.cadence.seeding.Seeder

/** Root seeder for the Firefly desk sample (`./grind db:seed` / boot when empty). */
class DatabaseSeeder : Seeder() {
    override fun run() {
        call(
            ProjectSeeder::class.java,
            TaskSeeder::class.java,
            NoteSeeder::class.java,
            OrderSeeder::class.java,
        )
    }
}
