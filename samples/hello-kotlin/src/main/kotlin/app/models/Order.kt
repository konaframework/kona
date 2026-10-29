package app.models

import org.konaframework.cadence.Cadence
import org.konaframework.cadence.CadenceCompanion
import org.konaframework.cadence.relations.HasMany

class Order : Cadence<Order>() {
    override val table = "orders"
    override val timestamps = false
    override val fillable = setOf("name")

    fun lines(): HasMany<Line> = hasMany(Line::class.java)

    companion object : CadenceCompanion<Order>(Order::class.java)
}

class Line : Cadence<Line>() {
    override val table = "lines"
    override val timestamps = false
    override val fillable = setOf("order_id", "sku")

    fun order() = belongsTo(Order::class.java, "order_id")

    companion object : CadenceCompanion<Line>(Line::class.java)
}
