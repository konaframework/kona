package app.models

import org.konaframework.cadence.Cadence

class Order : Cadence<Order>() {
    override val table = "orders"
    override val timestamps = false
    override val fillable = setOf("name")
}

class Line : Cadence<Line>() {
    override val table = "lines"
    override val timestamps = false
    override val fillable = setOf("sku")
}
