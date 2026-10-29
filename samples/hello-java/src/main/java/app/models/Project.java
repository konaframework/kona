package app.models;

import org.konaframework.cadence.Cadence;
import org.konaframework.cadence.CadenceCompanion;
import org.konaframework.cadence.relations.HasMany;

import java.util.Set;

public class Project extends Cadence<Project> {
    public static final CadenceCompanion<Project> QUERY = Cadence.of(Project.class);

    @Override
    public String getTable() {
        return "projects";
    }

    @Override
    public boolean getTimestamps() {
        return false;
    }

    @Override
    public Set<String> getFillable() {
        return Set.of("name", "active");
    }

    public HasMany<Task> tasks() {
        return hasMany(Task.class);
    }
}
