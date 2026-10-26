package app.models;

import org.konaframework.cadence.Cadence;

public class Note extends Cadence<Note> {
    @Override
    public String getTable() {
        return "notes";
    }

    @Override
    public boolean getTimestamps() {
        return false;
    }
}
