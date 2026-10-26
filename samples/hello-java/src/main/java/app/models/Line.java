package app.models;

import org.konaframework.cadence.Cadence;

public class Line extends Cadence<Line> {
    @Override
    public String getTable() {
        return "lines";
    }

    @Override
    public boolean getTimestamps() {
        return false;
    }
}
