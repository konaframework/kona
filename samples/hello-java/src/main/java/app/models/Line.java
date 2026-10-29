package app.models;

import org.konaframework.cadence.Cadence;
import org.konaframework.cadence.CadenceCompanion;
import org.konaframework.cadence.relations.BelongsTo;

import java.util.Set;

public class Line extends Cadence<Line> {
    public static final CadenceCompanion<Line> QUERY = Cadence.of(Line.class);

    @Override
    public String getTable() {
        return "lines";
    }

    @Override
    public boolean getTimestamps() {
        return false;
    }

    @Override
    public Set<String> getFillable() {
        return Set.of("order_id", "sku");
    }

    public BelongsTo<Order> order() {
        return belongsTo(Order.class, "order_id");
    }
}
