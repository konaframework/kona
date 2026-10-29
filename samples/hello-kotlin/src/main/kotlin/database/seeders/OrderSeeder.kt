package database.seeders

import app.models.Line
import app.models.Order
import org.konaframework.cadence.seeding.Seeder

class OrderSeeder : Seeder() {
    override fun run() {
        val order = Order().forceFill(mapOf("name" to "Ada")).also { it.save() }
        listOf("SKU-A", "SKU-B").forEach { sku ->
            Line().forceFill(mapOf("order_id" to order.getKey(), "sku" to sku)).save()
        }
    }
}
