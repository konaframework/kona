package database.seeders;

import app.models.Line;
import app.models.Order;
import org.konaframework.cadence.seeding.Seeder;

import java.util.Map;

public class OrderSeeder extends Seeder {
    @Override
    public void run() {
        Order order = new Order().forceFill(Map.of("name", "Ada"));
        order.save();
        for (String sku : new String[]{"SKU-A", "SKU-B"}) {
            new Line().forceFill(Map.of("order_id", order.getKey(), "sku", sku)).save();
        }
    }
}
