package org.konaframework.firefly

import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.widgets.NavIcons
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

    /**
     * Lets the user fold the group. Rendered as `<details>`, so it needs no script. Groups stay
     * independently open unless the panel calls [Panel.navigationAccordion].
     */
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
        return """<span class="ff-nav-icon-wrap">${navIconMarkup(NavIcons.resolve(raw))}</span>"""
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
 * Level-1 roots for [NavigationType.APP] and [NavigationType.INVERSE]: ungrouped items stay as
 * roots; each navigation group becomes a cluster whose members are children. APP shows the roots
 * in the sidebar and the active root’s children in the top bar. INVERSE swaps those two slots.
 * SIDE keeps [renderGrouped] headings instead.
 */
internal fun appLevelOnes(
    items: List<NavigationItem>,
    declared: List<NavigationGroup>,
    get: Get,
): List<NavigationItem> {
    if (items.none { it.groupName() != null }) return orderItems(items, get)
    val (loose, buckets) = bucketGroups(items, declared, get)
    val roots = ArrayList<NavigationItem>(loose.size + buckets.size)
    roots.addAll(loose)
    for (bucket in buckets) {
        val root = NavigationItem(bucket.group.label).label(bucket.group.label).cluster(true)
        val icon = bucket.group.navigationIcon()
        if (!icon.isNullOrBlank()) root.icon(icon)
        // Buckets only exist when they have members.
        val firstUrl = bucket.items.first().urlText(get)
        if (firstUrl.isNotEmpty()) root.url(firstUrl)
        for (member in bucket.items) root.child(member)
        roots.add(root)
    }
    return roots
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
    drawer: Boolean = false,
): String {
    // Top bar (`inline`) turns each group into a dropdown trigger. The mobile drawer forces
    // every group into a collapsible accordion, whatever the desktop heading style is.
    if (items.none { it.groupName() != null }) {
        // Inline top bar: nested links are dropdowns. Drawer and sidebar: nested lists.
        return renderItems(items, path, spa, get, nested, menu = inline)
    }
    val (loose, buckets) = bucketGroups(items, groups, get)
    val out = StringBuilder()
    if (inline) {
        val rows = StringBuilder()
        for (item in loose) rows.append(renderItem(item, path, spa, get, nested, true))
        for (bucket in buckets) {
            val inner = StringBuilder()
            for (item in bucket.items) inner.append(renderItem(item, path, spa, get, nested, true))
            val holdsActive = bucket.items.any { itemOrDescendantActive(it, path, get) }
            val active = if (holdsActive) " ff-active" else ""
            val label = escapeHtml(bucket.group.label)
            rows.append(
                """<li class="ff-nav-item ff-cluster ff-nav-group ff-nav-dropdown$active">""" +
                    """<button type="button" class="ff-nav-dropdown-trigger" aria-haspopup="menu" aria-expanded="false">""" +
                    """${bucket.group.iconHtml()}<span class="ff-nav-label">$label</span></button>""" +
                    """<ul class="ff-nav-list" role="menu">$inner</ul></li>""",
            )
        }
        return """<ul class="ff-nav-list">$rows</ul>"""
    }
    out.append(renderItems(loose, path, spa, get, nested))
    for (bucket in buckets) {
        val list = renderItems(bucket.items, path, spa, get, nested)
        val heading = bucket.group.iconHtml() + """<span class="ff-nav-label">${escapeHtml(bucket.group.label)}</span>"""
        val name = escapeHtml(bucket.group.label)
        val holdsActive = bucket.items.any { itemOrDescendantActive(it, path, get) }
        val activeClass = if (holdsActive) " ff-nav-group-active" else ""
        if (bucket.group.isCollapsible() || drawer) {
            val open = if (drawer) {
                if (holdsActive) " open" else ""
            } else if (!bucket.group.isCollapsed() || holdsActive) {
                " open"
            } else {
                ""
            }
            out.append(
                """<details class="ff-nav-group ff-nav-group-collapsible$activeClass" data-ff-nav-group="$name"$open><summary class="ff-nav-group-label">$heading</summary>$list</details>""",
            )
        } else {
            out.append(
                """<section class="ff-nav-group$activeClass" data-ff-nav-group="$name"><h3 class="ff-nav-group-label">$heading</h3>$list</section>""",
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
