package org.konaframework.firefly

import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.htmlAttributes
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.widgets.NavIcons
import org.konaframework.icons.Icons
import java.util.function.Function
import java.util.function.Predicate

/**
 * Where panel navigation links sit in the chrome.
 *
 * - [APP] — level 1 in the sidebar; levels 2–3 of the active level-1 item in the top bar (Shamar shell).
 *   Navigation groups promote to level-1 clusters; their members render in the top bar.
 * - [INVERSE] — the inverse of [APP]: level 1 in the top bar; levels 2–3 of the active level-1 item
 *   in the sidebar. Groups promote to top-bar clusters; their members render in the sidebar.
 * - [SIDE] — the full tree in the sidebar (groups are headings).
 * - [TOP] — the full tree in the top bar (no sidebar column; groups are inline clusters).
 */
enum class NavigationType {
    APP,
    INVERSE,
    SIDE,
    TOP,
}

/**
 * One node in a panel navigation tree. Level 1 is the root. Level 4 fails when the node is added.
 */
class NavigationItem(val name: String) {
    private var labelLiteral: String = name
    private var labelUsing: Function<Get, String>? = null
    private var urlLiteral: String = ""
    private var urlUsing: Function<Get, String>? = null
    private var iconLiteral: String? = null
    private var iconUsing: Function<Get, String>? = null
    private var attributesLiteral: Map<String, String> = emptyMap()
    private var attributesUsing: Function<Get, Map<String, String>>? = null
    private var clusterFlag: Boolean = false
    private var matchPrefixValue: String? = null
    private var dashboardFlag: Boolean = false
    private var visibleLiteral: Boolean = true
    private var visibleUsing: Predicate<Get>? = null
    private var groupValue: String? = null
    private var sortValue: Int? = null
    private var badgeUsing: Function<Get, NavigationBadge?>? = null
    internal var depth: Int = 1
    internal val children = ArrayList<NavigationItem>()

    init {
        if (name.isBlank()) throw FireflyException("Navigation item name is blank")
    }

    fun label(value: String): NavigationItem = apply {
        if (value.isBlank()) throw FireflyException("Navigation item [$name] label is blank")
        labelLiteral = value
        labelUsing = null
    }

    fun label(provider: Function<Get, String>): NavigationItem = apply {
        labelUsing = provider
        labelLiteral = ""
    }

    fun url(value: String): NavigationItem = apply {
        urlLiteral = value
        urlUsing = null
    }

    fun url(provider: Function<Get, String>): NavigationItem = apply {
        urlUsing = provider
        urlLiteral = ""
    }

    /**
     * Navigation icon via kona-icons (`heroicon-o-folder` or short `folder`).
     * Unset items use [NavIcons.HOME] for the dashboard root and [NavIcons.DEFAULT] otherwise.
     */
    fun icon(value: String): NavigationItem = apply {
        iconLiteral = value
        iconUsing = null
    }

    fun icon(provider: Function<Get, String>): NavigationItem = apply {
        iconUsing = provider
        iconLiteral = null
    }

    /** Marks this item as the panel home so the default icon is [NavIcons.HOME]. */
    internal fun dashboard(on: Boolean = true): NavigationItem = apply { dashboardFlag = on }

    fun iconName(get: Get): String {
        val provider = iconUsing
        val raw = if (provider != null) provider.apply(get) else iconLiteral
        if (!raw.isNullOrBlank()) return NavIcons.resolve(raw)
        return NavIcons.defaultForDashboard(dashboardFlag || name == "dashboard")
    }

    fun extraAttributes(values: Map<String, String>): NavigationItem = apply {
        attributesLiteral = LinkedHashMap(values)
        attributesUsing = null
    }

    fun extraAttributes(provider: Function<Get, Map<String, String>>): NavigationItem = apply {
        attributesUsing = provider
        attributesLiteral = emptyMap()
    }

    /**
     * Shows or hides the item (Filament `visible`). Hiding an item hides its children. Resource
     * and page items already follow [Resource.canViewAny] and [PanelPage.canAccess].
     */
    fun visible(on: Boolean): NavigationItem = apply {
        visibleLiteral = on
        visibleUsing = null
    }

    fun visible(predicate: Predicate<Get>): NavigationItem = apply { visibleUsing = predicate }

    fun isVisible(get: Get): Boolean {
        val predicate = visibleUsing
        if (predicate != null) return predicate.test(get)
        return visibleLiteral
    }

    fun cluster(on: Boolean): NavigationItem = apply { clusterFlag = on }

    /**
     * Gathers this level-1 item under the sidebar heading [name]. Declare the heading's order and
     * look with `Panel.navigationGroups(...)`. Ignored on nested items.
     */
    fun group(name: String): NavigationItem = apply {
        if (name.isBlank()) throw FireflyException("Navigation item [${this.name}] group is blank")
        groupValue = name
    }

    /** Orders siblings, lowest first. Items without a sort follow, by label. */
    fun sort(value: Int): NavigationItem = apply { sortValue = value }

    /** A fixed badge at the end of the link. */
    fun badge(value: NavigationBadge): NavigationItem = apply { badgeUsing = Function { value } }

    /** A badge computed on every render; return null for none. */
    fun badge(provider: Function<Get, NavigationBadge?>): NavigationItem = apply { badgeUsing = provider }

    fun groupName(): String? = groupValue

    fun sortValue(): Int? = sortValue

    fun badgeFor(get: Get): NavigationBadge? {
        val provider = badgeUsing ?: return null
        return provider.apply(get)
    }

    /**
     * Keeps this item highlighted across a whole URL subtree instead of only its own link.
     * A resource nav entry points at its list page but stays active on create/edit/view too.
     */
    internal fun matchPrefix(value: String): NavigationItem = apply { matchPrefixValue = value }

    internal fun isActive(path: String, get: Get): Boolean {
        val prefix = matchPrefixValue
        // matchPrefix: highlight across a subtree (resource list → create/edit/view).
        if (prefix != null) return activeItem(path, prefix)
        // Otherwise exact URL only — panel home `/admin` must not stay active on `/admin/users`.
        return path == urlText(get)
    }

    fun child(item: NavigationItem): NavigationItem = apply {
        item.depth = depth + 1
        children.add(item)
    }

    internal fun assertDepth(level: Int) {
        if (level > 3) throw FireflyException("Navigation item [$name] is nested past level 3")
        for (child in children) child.assertDepth(level + 1)
    }

    fun isCluster(): Boolean = clusterFlag

    fun labelText(get: Get): String {
        val provider = labelUsing
        if (provider != null) {
            val value = provider.apply(get)
            if (value.isBlank()) throw FireflyException("Navigation item [$name] label is blank")
            return value
        }
        return labelLiteral
    }

    fun urlText(get: Get): String {
        val provider = urlUsing
        if (provider != null) {
            val value = provider.apply(get)
            if (value.isBlank()) throw FireflyException("Navigation item [$name] url is blank")
            return value
        }
        return urlLiteral
    }

    fun attributes(get: Get): Map<String, String> {
        val provider = attributesUsing
        if (provider != null) return provider.apply(get)
        return attributesLiteral
    }

    fun find(target: String): NavigationItem? {
        if (name == target) return this
        for (child in children) {
            val found = child.find(target)
            if (found != null) return found
        }
        return null
    }
}

internal fun activeItem(path: String, url: String): Boolean {
    if (path == url) return true
    if (url.isEmpty() || url == "/") return false
    return path.startsWith(url + "/")
}

internal fun renderItems(
    items: List<NavigationItem>,
    path: String,
    spa: Boolean,
    get: Get,
    nested: Boolean,
    menu: Boolean = false,
    menuRole: Boolean = false,
): String {
    val ordered = orderItems(items, get)
    if (ordered.isEmpty()) return ""
    val out = StringBuilder()
    for (item in ordered) out.append(renderItem(item, path, spa, get, nested, menu))
    val role = if (menuRole) """ role="menu"""" else ""
    return """<ul class="ff-nav-list"$role>$out</ul>"""
}

internal fun renderItem(
    item: NavigationItem,
    path: String,
    spa: Boolean,
    get: Get,
    nested: Boolean,
    menu: Boolean,
): String {
    val url = item.urlText(get)
    // Clusters (APP group roots, manual parents) stay highlighted when a descendant matches.
    val active = if (itemOrDescendantActive(item, path, get)) " ff-active" else ""
    val cluster = if (item.isCluster()) " ff-cluster" else ""
    val navigate = if (spa && url.isNotEmpty()) " wire:navigate" else ""
    val href = if (url.isEmpty()) "" else " href=\"${escapeHtml(url)}\""
    val iconHtml = navIconMarkup(item.iconName(get))
    val badge = item.badgeFor(get)
    val badgeHtml = if (badge == null) {
        ""
    } else {
        """<span class="ff-nav-badge" data-ff-color="${escapeHtml(badge.color)}">${escapeHtml(badge.text)}</span>"""
    }
    val label = escapeHtml(item.labelText(get))
    val inner = """<span class="ff-nav-icon-wrap">$iconHtml</span><span class="ff-nav-label">$label</span>$badgeHtml"""
    // Top-bar menus: children open in a dropdown. Sidebar nesting stays an inline list.
    val childHtml = if (nested || menu) renderItems(item.children, path, spa, get, true, menu, menu) else ""
    val hasMenu = menu && childHtml.isNotEmpty()
    val dropdown = if (hasMenu) " ff-nav-dropdown" else ""
    val out = StringBuilder()
    out.append("""<li class="ff-nav-item$cluster$active$dropdown">""")
    if (hasMenu && url.isEmpty()) {
        out.append(
            """<button type="button" class="ff-nav-dropdown-trigger" aria-haspopup="menu" aria-expanded="false">$inner</button>""",
        )
    } else {
        out.append("""<a$href$navigate${htmlAttributes(item.attributes(get))}>$inner</a>""")
        if (hasMenu) {
            out.append(
                """<button type="button" class="ff-nav-dropdown-trigger" aria-haspopup="menu" aria-expanded="false" aria-label="Open $label menu"></button>""",
            )
        }
    }
    if (childHtml.isNotEmpty()) out.append(childHtml)
    out.append("</li>")
    return out.toString()
}

/** 16×16 markup matching `.ff-nav-icon`, so a 24 viewBox lands on whole pixels. */
internal fun navIconMarkup(name: String): String {
    val svg = try {
        Icons.svg(name)
    } catch (_: Exception) {
        Icons.svg(NavIcons.DEFAULT)
    }
    return svg.className("ff-nav-icon")
        .attr("width", "16")
        .attr("height", "16")
        .attr("aria-hidden", "true")
        .toHtml()
}
