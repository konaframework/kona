package app.models;

import org.konaframework.cadence.Cadence;
import org.konaframework.cadence.CadenceCompanion;

import java.util.Set;

public class Note extends Cadence<Note> {
    public static final CadenceCompanion<Note> QUERY = Cadence.of(Note.class);

    @Override
    public String getTable() {
        return "notes";
    }

    @Override
    public boolean getTimestamps() {
        return false;
    }

    @Override
    public Set<String> getFillable() {
        return Set.of("name", "status", "due");
    }
}
