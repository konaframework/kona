package org.konaframework.firefly

import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.htmlAttributes
import org.konaframework.firefly.support.FireflyException
import java.util.function.Function

enum class NavigationType {
    APP,
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
    private var attributesLiteral: Map<String, String> = emptyMap()
    private var attributesUsing: Function<Get, Map<String, String>>? = null
    private var clusterFlag: Boolean = false
    private var matchPrefixValue: String? = null
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

    fun extraAttributes(values: Map<String, String>): NavigationItem = apply {
        attributesLiteral = LinkedHashMap(values)
        attributesUsing = null
    }

    fun extraAttributes(provider: Function<Get, Map<String, String>>): NavigationItem = apply {
        attributesUsing = provider
        attributesLiteral = emptyMap()
    }

    fun cluster(on: Boolean): NavigationItem = apply { clusterFlag = on }

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
): String {
    if (items.isEmpty()) return ""
    val out = StringBuilder()
    val ordered = items.sortedBy { it.labelText(get) }
    for (item in ordered) {
        val url = item.urlText(get)
        val active = if (item.isActive(path, get)) " ff-active" else ""
        val cluster = if (item.isCluster()) " ff-cluster" else ""
        val navigate = if (spa && url.isNotEmpty()) " wire:navigate" else ""
        val href = if (url.isEmpty()) "" else " href=\"${escapeHtml(url)}\""
        out.append(
            """<li class="ff-nav-item$cluster$active"><a$href$navigate${htmlAttributes(item.attributes(get))}>${escapeHtml(item.labelText(get))}</a>""",
        )
        if (nested) out.append(renderItems(item.children, path, spa, get, true))
        out.append("</li>")
    }
    return """<ul class="ff-nav-list">$out</ul>"""
}
