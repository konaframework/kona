package app.models;

import org.konaframework.cadence.Cadence;

public class Order extends Cadence<Order> {
    @Override
    public String getTable() {
        return "orders";
    }

    @Override
    public boolean getTimestamps() {
        return false;
    }
}
