package database.seeders;

import org.konaframework.cadence.seeding.Seeder;

public class DatabaseSeeder extends Seeder {
    @Override
    public void run() {
        call(
            ProjectSeeder.class,
            TaskSeeder.class,
            NoteSeeder.class,
            OrderSeeder.class
        );
    }
}
