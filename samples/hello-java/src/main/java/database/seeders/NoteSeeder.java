package database.seeders;

import app.models.Note;
import org.konaframework.cadence.seeding.Seeder;

import java.util.HashMap;
import java.util.Map;

public class NoteSeeder extends Seeder {
    @Override
    public void run() {
        Map<String, Object> ada = new HashMap<>();
        ada.put("name", "Ada");
        ada.put("status", "open");
        ada.put("due", "2026-11-01 09:00:00");
        new Note().forceFill(ada).save();

        Map<String, Object> ship = new HashMap<>();
        ship.put("name", "Ship notes");
        ship.put("status", "done");
        ship.put("due", null);
        new Note().forceFill(ship).save();
    }
}
