package org.konaframework.firefly.widgets

import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.tables.Table
import org.konaframework.icons.Icons
import java.util.function.Function

open class TableWidget protected constructor(name: String) : Widget(name) {
    private var table: Table<*, *>? = null

    fun table(value: Table<*, *>): TableWidget = apply { table = value }

    override fun extraAttributes(values: Map<String, String>): TableWidget {
        super.extraAttributes(values)
        return this
    }

    override fun renderBody(schema: Schema): String {
        val current = table
        if (current == null) throw FireflyException("Table widget [$name] has no table")
        return WidgetAssets.tag(WidgetScripts.WIDGET) + wrapDashboardBody(schema, current.render())
    }

    companion object {
        @JvmStatic
        fun make(name: String): TableWidget = TableWidget(name)
    }
}

open class AccountWidget protected constructor(name: String) : Widget(name) {
    private val nameSetting = TextSetting()
    private val emailSetting = TextSetting()
    private val avatarSetting = TextSetting()

    fun accountName(value: String): AccountWidget = apply { nameSetting.setLiteral(value) }

    fun accountName(provider: Function<Get, String>): AccountWidget = apply { nameSetting.setUsing(provider) }

    fun email(value: String): AccountWidget = apply { emailSetting.setLiteral(value) }

    fun email(provider: Function<Get, String>): AccountWidget = apply { emailSetting.setUsing(provider) }

    fun avatar(value: String): AccountWidget = apply { avatarSetting.setLiteral(value) }

    fun avatar(provider: Function<Get, String>): AccountWidget = apply { avatarSetting.setUsing(provider) }

    override fun extraAttributes(values: Map<String, String>): AccountWidget {
        super.extraAttributes(values)
        return this
    }

    override fun renderBody(schema: Schema): String {
        val get = WidgetBridge.reader(schema, this)
        val account = nameSetting.resolve(get)
        if (account == null || account.isBlank()) throw FireflyException("Account widget [$name] has no name")
        val email = emailSetting.resolve(get)
        val avatar = avatarSetting.resolve(get)
        val emailHtml = if (email == null || email.isEmpty()) "" else """<p class="ff-account-email">${escapeHtml(email)}</p>"""
        val avatarHtml = if (avatar == null || avatar.isEmpty()) {
            ""
        } else {
            """<img class="ff-account-avatar" src="${escapeHtml(avatar)}" alt=""/>"""
        }
        val inner = """<div class="ff-account">$avatarHtml<p class="ff-account-name">${escapeHtml(account)}</p>$emailHtml</div>"""
        return WidgetAssets.tag(WidgetScripts.WIDGET) + wrapDashboardBody(schema, inner)
    }

    companion object {
        @JvmStatic
        fun make(name: String): AccountWidget = AccountWidget(name)
    }
}

data class ListWidgetColumn(val label: String, val attribute: String)

/**
 * Card with a simple multi-column list (Shamar `ListWidget`).
 */
open class ListWidget protected constructor(name: String) : Widget(name) {
    private var columnDefs: List<ListWidgetColumn> = emptyList()
    private var recordsLiteral: List<Map<String, Any?>> = emptyList()
    private var recordsUsing: Function<Get, List<Map<String, Any?>>>? = null
    private var limitValue: Int = 5

    fun columns(vararg columns: ListWidgetColumn): ListWidget = apply {
        columnDefs = columns.toList()
    }

    fun columns(columns: List<ListWidgetColumn>): ListWidget = apply {
        columnDefs = columns
    }

    fun records(rows: List<Map<String, Any?>>): ListWidget = apply {
        recordsLiteral = rows
        recordsUsing = null
    }

    fun records(provider: Function<Get, List<Map<String, Any?>>>): ListWidget = apply {
        recordsUsing = provider
        recordsLiteral = emptyList()
    }

    fun limit(value: Int): ListWidget = apply { limitValue = maxOf(0, value) }

    override fun extraAttributes(values: Map<String, String>): ListWidget {
        super.extraAttributes(values)
        return this
    }

    override fun renderBody(schema: Schema): String {
        val get = WidgetBridge.reader(schema, this)
        val rows = if (shouldDefer(schema)) {
            emptyList()
        } else {
            val provider = recordsUsing
            val all = if (provider != null) provider.apply(get) else recordsLiteral
            if (limitValue <= 0) emptyList() else all.take(limitValue)
        }
        val headChrome = headingHtml(schema) + descriptionHtml(schema)
        val table = if (shouldDefer(schema)) {
            ""
        } else if (rows.isEmpty()) {
            """<p class="ff-widget-empty">No records to display.</p>"""
        } else {
            val head = columnDefs.joinToString("") {
                """<th>${escapeHtml(it.label)}</th>"""
            }
            val body = rows.joinToString("") { row ->
                val cells = columnDefs.mapIndexed { index, col ->
                    val raw = cellAt(row, col.attribute)
                    val text = formatCell(raw)
                    val url = row["url"]?.toString()
                    if (index == 0 && !url.isNullOrBlank()) {
                        """<td><a href="${escapeHtml(url)}" class="ff-list-widget-link">${escapeHtml(text)}</a></td>"""
                    } else {
                        """<td>${escapeHtml(text)}</td>"""
                    }
                }.joinToString("")
                """<tr>$cells</tr>"""
            }
            """<div class="ff-list-widget-scroll"><table class="ff-list-widget-table"><thead><tr>$head</tr></thead><tbody>$body</tbody></table></div>"""
        }
        val inner = """<div class="ff-list-widget ff-desk-widget">$headChrome$table</div>"""
        return WidgetAssets.tag(WidgetScripts.WIDGET) + wrapDashboardBody(schema, inner)
    }

    companion object {
        @JvmStatic
        fun make(name: String): ListWidget = ListWidget(name)

        private fun cellAt(record: Map<String, Any?>, path: String): Any? {
            val parts = path.split('.')
            var current: Any? = record
            for (part in parts) {
                if (current == null) return null
                current = when (current) {
                    is Map<*, *> -> current[part]
                    else -> return null
                }
            }
            return current
        }

        private fun formatCell(value: Any?): String {
            if (value == null) return "—"
            if (value is String && value.isEmpty()) return "—"
            return value.toString()
        }
    }
}

/**
 * General-purpose card widget (Shamar `CardWidget`).
 */
open class CardWidget protected constructor(name: String) : Widget(name) {
    private var htmlLiteral: String? = null
    private var htmlUsing: Function<Get, String>? = null

    fun html(value: String): CardWidget = apply {
        htmlLiteral = value
        htmlUsing = null
    }

    fun html(provider: Function<Get, String>): CardWidget = apply {
        htmlUsing = provider
        htmlLiteral = null
    }

    override fun extraAttributes(values: Map<String, String>): CardWidget {
        super.extraAttributes(values)
        return this
    }

    override fun renderBody(schema: Schema): String {
        val get = WidgetBridge.reader(schema, this)
        val content = if (shouldDefer(schema)) {
            ""
        } else {
            val provider = htmlUsing
            when {
                provider != null -> provider.apply(get)
                htmlLiteral != null -> htmlLiteral!!
                else -> ""
            }
        }
        val body = if (content.isBlank() && !shouldDefer(schema)) {
            """<p class="ff-widget-empty">No card content configured.</p>"""
        } else {
            """<div class="ff-card-widget-body">$content</div>"""
        }
        val inner = """<div class="ff-card-widget ff-desk-widget">$body</div>"""
        return WidgetAssets.tag(WidgetScripts.WIDGET) + wrapDashboardBody(schema, inner)
    }

    companion object {
        @JvmStatic
        fun make(name: String): CardWidget = CardWidget(name)
    }
}

data class NavigationCard(
    val label: String,
    val href: String,
    val icon: String? = null,
)

/**
 * Link cards for panel navigation roots (Shamar `NavigationCardsWidget`).
 */
open class NavigationCardsWidget protected constructor(name: String) : Widget(name) {
    private var cardsLiteral: List<NavigationCard> = emptyList()
    private var cardsUsing: Function<Get, List<NavigationCard>>? = null
    private var emptyMessage: String = "No resources registered yet."

    init {
        sort(1000)
        columnSpanFull()
    }

    fun cards(items: List<NavigationCard>): NavigationCardsWidget = apply {
        cardsLiteral = items
        cardsUsing = null
    }

    fun cards(provider: Function<Get, List<NavigationCard>>): NavigationCardsWidget = apply {
        cardsUsing = provider
        cardsLiteral = emptyList()
    }

    fun emptyMessage(message: String): NavigationCardsWidget = apply { emptyMessage = message }

    override fun extraAttributes(values: Map<String, String>): NavigationCardsWidget {
        super.extraAttributes(values)
        return this
    }

    override fun renderBody(schema: Schema): String {
        val get = WidgetBridge.reader(schema, this)
        @Suppress("UNCHECKED_CAST")
        val fromState = get.get("navigationCards") as? List<*>
        val items = when {
            shouldDefer(schema) -> emptyList()
            cardsUsing != null -> cardsUsing!!.apply(get)
            cardsLiteral.isNotEmpty() -> cardsLiteral
            fromState != null -> fromState.mapNotNull { row ->
                when (row) {
                    is NavigationCard -> row
                    is Map<*, *> -> {
                        val labelRaw = row["label"]
                        val hrefRaw = row["href"]
                        if (labelRaw == null || hrefRaw == null) return@mapNotNull null
                        val label = labelRaw.toString()
                        val href = hrefRaw.toString()
                        val iconRaw = row["icon"]
                        NavigationCard(label, href, iconRaw?.toString())
                    }
                    else -> null
                }
            }
            else -> emptyList()
        }
        val grid = if (shouldDefer(schema)) {
            ""
        } else if (items.isEmpty()) {
            """<p class="ff-widget-empty">${escapeHtml(emptyMessage)}</p>"""
        } else {
            items.joinToString("") { card ->
                val iconName = NavIcons.resolve(card.icon)
                val iconHtml = try {
                    Icons.svg(iconName).className("ff-nav-icon").toHtml()
                } catch (_: Exception) {
                    Icons.svg(NavIcons.DEFAULT).className("ff-nav-icon").toHtml()
                }
                """<a class="ff-nav-card" href="${escapeHtml(card.href)}"><span class="ff-nav-card-icon">$iconHtml</span><span class="ff-nav-card-label">${escapeHtml(card.label)}</span><span class="ff-nav-card-hint">Open ${escapeHtml(card.label)}</span></a>"""
            }.let { """<div class="ff-nav-cards">$it</div>""" }
        }
        val inner = """<div class="ff-nav-cards-widget ff-desk-widget">$grid</div>"""
        return WidgetAssets.tag(WidgetScripts.WIDGET) + wrapDashboardBody(schema, inner)
    }

    companion object {
        @JvmStatic
        fun make(name: String): NavigationCardsWidget = NavigationCardsWidget(name)
    }
}

/** Resolve Shamar short names and heroicon ids for navigation. */
object NavIcons {
    const val DEFAULT = "heroicon-o-squares-2x2"
    const val HOME = "heroicon-o-home"

    @JvmStatic
    fun resolve(name: String?): String {
        if (name.isNullOrBlank()) return DEFAULT
        val trimmed = name.trim()
        if (trimmed.startsWith("heroicon-")) return trimmed
        return "heroicon-o-$trimmed"
    }

    @JvmStatic
    fun defaultForDashboard(isDashboard: Boolean): String = if (isDashboard) HOME else DEFAULT
}
