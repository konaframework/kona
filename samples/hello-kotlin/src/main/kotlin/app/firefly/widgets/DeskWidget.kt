package app.firefly.widgets

import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.widgets.Widget

class DeskWidget : Widget("desk") {
    override fun renderBody(schema: Schema): String = """<p class="ff-desk-widget">Open orders</p>"""
}
