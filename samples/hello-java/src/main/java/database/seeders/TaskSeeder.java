package database.seeders;

import app.models.Project;
import app.models.Task;
import org.konaframework.cadence.seeding.Seeder;

import java.util.HashMap;
import java.util.Map;

public class TaskSeeder extends Seeder {
    @Override
    public void run() {
        Project desk = Project.QUERY.query().where("name", "Desk").first();
        Project lab = Project.QUERY.query().where("name", "Lab").first();
        seed("Wire combobox", "open", desk);
        seed("Seed sample data", "done", desk);
        seed("Calibrate sensors", "open", lab);
    }

    private static void seed(String name, String status, Project project) {
        Map<String, Object> row = new HashMap<>();
        row.put("name", name);
        row.put("status", status);
        row.put("project_id", project == null ? null : project.getKey());
        new Task().forceFill(row).save();
    }
}
