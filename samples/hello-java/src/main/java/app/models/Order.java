package app.models;

import org.konaframework.cadence.Cadence;
import org.konaframework.cadence.CadenceCompanion;
import org.konaframework.cadence.relations.HasMany;

import java.util.Set;

public class Order extends Cadence<Order> {
    public static final CadenceCompanion<Order> QUERY = Cadence.of(Order.class);

    @Override
    public String getTable() {
        return "orders";
    }

    @Override
    public boolean getTimestamps() {
        return false;
    }

    @Override
    public Set<String> getFillable() {
        return Set.of("name");
    }

    public HasMany<Line> lines() {
        return hasMany(Line.class);
    }
}
