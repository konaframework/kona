package database.seeders;

import app.models.Project;
import org.konaframework.cadence.seeding.Seeder;

import java.util.List;
import java.util.Map;

public class ProjectSeeder extends Seeder {
    @Override
    public void run() {
        List.of(
            Map.<String, Object>of("name", "Desk", "active", true),
            Map.<String, Object>of("name", "Lab", "active", true),
            Map.<String, Object>of("name", "Archive", "active", false)
        ).forEach(row -> new Project().forceFill(row).save());
    }
}
