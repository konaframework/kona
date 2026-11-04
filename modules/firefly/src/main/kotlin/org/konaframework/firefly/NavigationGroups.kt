package org.konaframework.firefly

import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.widgets.NavIcons
import org.konaframework.icons.Icons
import java.util.function.Supplier

/**
 * A counter shown at the end of a navigation link (Filament `getNavigationBadge`). [color] is one
 * of `primary`, `success`, `warning`, `danger`, `info` or `gray`.
 */
class NavigationBadge @JvmOverloads constructor(val text: String, val color: String = "primary")

/**
 * A heading that gathers navigation links in the sidebar (Filament `NavigationGroup`). Declare the
 * order and look with `Panel.navigationGroups(...)`; items name their group with
 * `Resource.navigationGroup("Sales")`, `PanelPage.navigationGroup(...)` or `NavigationItem.group(...)`.
 * A group nobody declared still renders, after the declared ones, as a plain heading.
 */
class NavigationGroup private constructor(val label: String) {
    private var iconLiteral: String? = null
    private var collapsibleFlag: Boolean = false
    private var collapsedFlag: Boolean = false

    /** kona-icons name (short or `heroicon-o-…`) drawn beside the heading. */
    fun icon(name: String): NavigationGroup = apply { iconLiteral = name }

    /** Lets the user fold the group. Rendered as `<details>`, so it needs no script. */
    @JvmOverloads
    fun collapsible(on: Boolean = true): NavigationGroup = apply { collapsibleFlag = on }

    /** Starts folded (and makes the group collapsible). A group holding the current page stays open. */
    @JvmOverloads
    fun collapsed(on: Boolean = true): NavigationGroup = apply {
        collapsedFlag = on
        if (on) collapsibleFlag = true
    }

    fun isCollapsible(): Boolean = collapsibleFlag

    fun isCollapsed(): Boolean = collapsedFlag

    fun navigationIcon(): String? = iconLiteral

    internal fun iconHtml(): String {
        val raw = iconLiteral
        if (raw.isNullOrBlank()) return ""
        val name = NavIcons.resolve(raw)
        val svg = try {
            Icons.svg(name)
        } catch (_: Exception) {
            Icons.svg(NavIcons.DEFAULT)
        }
        return """<span class="ff-nav-icon-wrap">${svg.className("ff-nav-icon").attr("aria-hidden", "true").toHtml()}</span>"""
    }

    companion object {
        @JvmStatic
        fun make(label: String): NavigationGroup {
            if (label.isBlank()) throw FireflyException("Navigation group label is blank")
            return NavigationGroup(label)
        }
    }
}

/** Navigation settings a [Resource] or [PanelPage] carries into the discovered tree. */
internal class NavigationSettings {
    var group: String? = null
    var sort: Int? = null
    var label: String? = null
    var badgeProvider: Supplier<Any?>? = null
    var badgeColor: String = "primary"
    var register: Boolean = true

    fun group(value: String) {
        if (value.isBlank()) throw FireflyException("Navigation group is blank")
        group = value
    }

    fun label(value: String) {
        if (value.isBlank()) throw FireflyException("Navigation label is blank")
        label = value
    }

    fun badgeColor(value: String) {
        if (value.isBlank()) throw FireflyException("Navigation badge color is blank")
        badgeColor = value
    }

    /** The badge for now, or null when the provider is unset or answers null / blank. */
    fun badge(): NavigationBadge? {
        val provider = badgeProvider ?: return null
        val text = provider.get()?.toString()
        if (text.isNullOrBlank()) return null
        return NavigationBadge(text, badgeColor)
    }
}

private fun sortKey(item: NavigationItem): Int = item.sortValue() ?: Int.MAX_VALUE

/** Visible items by `sort` (unset last), then by label. */
internal fun orderItems(items: List<NavigationItem>, get: Get): List<NavigationItem> =
    items.filter { it.isVisible(get) }.sortedWith(compareBy<NavigationItem>({ sortKey(it) }, { it.labelText(get) }))

private class GroupBucket(val group: NavigationGroup, val items: List<NavigationItem>)

/** Ungrouped items first, then groups: declared ones in order, the rest as first met. Empty groups drop out. */
private fun bucketGroups(
    items: List<NavigationItem>,
    declared: List<NavigationGroup>,
    get: Get,
): Pair<List<NavigationItem>, List<GroupBucket>> {
    val ordered = orderItems(items, get)
    val loose = ordered.filter { it.groupName() == null }
    val byName = LinkedHashMap<String, NavigationGroup>()
    for (group in declared) byName[group.label] = group
    for (item in ordered) {
        val name = item.groupName() ?: continue
        if (!byName.containsKey(name)) byName[name] = NavigationGroup.make(name)
    }
    val buckets = ArrayList<GroupBucket>()
    for ((name, group) in byName) {
        val members = ordered.filter { it.groupName() == name }
        if (members.isNotEmpty()) buckets.add(GroupBucket(group, members))
    }
    return Pair(loose, buckets)
}

/**
 * Renders level-1 [items] gathered under [groups]. In the sidebar each group is a heading above its
 * own list; with [inline] (top bar) a group is a cluster entry that holds its items.
 */
internal fun renderGrouped(
    items: List<NavigationItem>,
    groups: List<NavigationGroup>,
    path: String,
    spa: Boolean,
    get: Get,
    nested: Boolean,
    inline: Boolean,
): String {
    if (items.none { it.groupName() != null }) return renderItems(items, path, spa, get, nested)
    val (loose, buckets) = bucketGroups(items, groups, get)
    val out = StringBuilder()
    if (inline) {
        val rows = StringBuilder()
        for (item in loose) rows.append(renderItem(item, path, spa, get, nested))
        for (bucket in buckets) {
            val inner = StringBuilder()
            for (item in bucket.items) inner.append(renderItem(item, path, spa, get, nested))
            rows.append(
                """<li class="ff-nav-item ff-cluster ff-nav-group"><a>${bucket.group.iconHtml()}<span class="ff-nav-label">${escapeHtml(bucket.group.label)}</span></a><ul class="ff-nav-list">$inner</ul></li>""",
            )
        }
        return """<ul class="ff-nav-list">$rows</ul>"""
    }
    out.append(renderItems(loose, path, spa, get, nested))
    for (bucket in buckets) {
        val list = renderItems(bucket.items, path, spa, get, nested)
        val heading = bucket.group.iconHtml() + """<span class="ff-nav-label">${escapeHtml(bucket.group.label)}</span>"""
        val name = escapeHtml(bucket.group.label)
        if (bucket.group.isCollapsible()) {
            val holdsActive = bucket.items.any { itemOrDescendantActive(it, path, get) }
            val open = if (!bucket.group.isCollapsed() || holdsActive) " open" else ""
            out.append(
                """<details class="ff-nav-group ff-nav-group-collapsible" data-ff-nav-group="$name"$open><summary class="ff-nav-group-label">$heading</summary>$list</details>""",
            )
        } else {
            out.append(
                """<section class="ff-nav-group" data-ff-nav-group="$name"><h3 class="ff-nav-group-label">$heading</h3>$list</section>""",
            )
        }
    }
    return out.toString()
}

internal fun itemOrDescendantActive(item: NavigationItem, path: String, get: Get): Boolean {
    if (item.isActive(path, get)) return true
    for (child in item.children) {
        if (itemOrDescendantActive(child, path, get)) return true
    }
    return false
}
