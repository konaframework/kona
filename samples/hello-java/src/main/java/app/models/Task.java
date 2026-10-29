package app.models;

import org.konaframework.cadence.Cadence;
import org.konaframework.cadence.CadenceCompanion;
import org.konaframework.cadence.relations.BelongsTo;

import java.util.Set;

public class Task extends Cadence<Task> {
    public static final CadenceCompanion<Task> QUERY = Cadence.of(Task.class);

    @Override
    public String getTable() {
        return "tasks";
    }

    @Override
    public boolean getTimestamps() {
        return false;
    }

    @Override
    public Set<String> getFillable() {
        return Set.of("project_id", "name", "status");
    }

    public BelongsTo<Project> project() {
        return belongsTo(Project.class, "project_id");
    }
}
