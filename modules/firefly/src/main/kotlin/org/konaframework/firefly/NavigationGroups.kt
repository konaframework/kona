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
    private var sortValue: Int? = null

    /** kona-icons name (short or `heroicon-o-…` / `heroicon-s-…`) drawn beside the heading. */
    fun icon(name: String): NavigationGroup = apply { iconLiteral = name }

    /**
     * Orders this group among top-level links and other groups, lowest first.
     * Unset matches an item with no [NavigationItem.sort] (both sort last). Ties keep
     * definition order, so a panel that sets no sort does not jump.
     */
    fun navigationSort(value: Int): NavigationGroup = apply { sortValue = value }

    fun navigationSort(): Int? = sortValue

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

/** Same default as [sortKey]: an unset group sort ties with an unset item sort. */
private fun groupSortKey(group: NavigationGroup): Int = group.navigationSort() ?: Int.MAX_VALUE

/** Visible items by `sort` (unset last), then by label. */
internal fun orderItems(items: List<NavigationItem>, get: Get): List<NavigationItem> =
    items.filter { it.isVisible(get) }.sortedWith(compareBy<NavigationItem>({ sortKey(it) }, { it.labelText(get) }))

private class GroupBucket(val group: NavigationGroup, val items: List<NavigationItem>)

private sealed class NavBlock {
    class Loose(val item: NavigationItem) : NavBlock()
    class Grouped(val bucket: GroupBucket) : NavBlock()
}

/**
 * One list of loose items and groups. Sorted by navigation sort (unset is [Int.MAX_VALUE], the
 * same as an item with no sort), then by definition order. Definition order is the previous
 * sidebar sequence: loose items (already by sort, then label), then declared groups, then
 * undeclared groups. A stable sort keeps that sequence on ties, so existing panels do not jump
 * until a group sets a sort.
 */
private fun orderedBlocks(
    items: List<NavigationItem>,
    declared: List<NavigationGroup>,
    get: Get,
): List<NavBlock> {
    val (loose, buckets) = bucketGroups(items, declared, get)
    val sequenced = ArrayList<Pair<Int, NavBlock>>(loose.size + buckets.size)
    for (item in loose) sequenced.add(sortKey(item) to NavBlock.Loose(item))
    for (bucket in buckets) sequenced.add(groupSortKey(bucket.group) to NavBlock.Grouped(bucket))
    return sequenced.sortedBy { it.first }.map { it.second }
}

/** Splits visible items into loose links and groups. Declared groups lead; the rest follow as first met. Empty groups drop out. */
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
 * roots; each navigation group becomes a cluster whose members are children. Roots follow
 * [orderedBlocks], so a group sort can place the cluster before a loose item. APP shows the
 * roots in the sidebar and the active root’s children in the top bar. INVERSE swaps those two
 * slots. SIDE keeps [renderGrouped] headings instead.
 */
internal fun appLevelOnes(
    items: List<NavigationItem>,
    declared: List<NavigationGroup>,
    get: Get,
): List<NavigationItem> {
    if (items.none { it.groupName() != null }) return orderItems(items, get)
    val roots = ArrayList<NavigationItem>()
    for (block in orderedBlocks(items, declared, get)) {
        when (block) {
            is NavBlock.Loose -> roots.add(block.item)
            is NavBlock.Grouped -> {
                val bucket = block.bucket
                val root = NavigationItem(bucket.group.label).label(bucket.group.label).cluster(true)
                val icon = bucket.group.navigationIcon()
                if (!icon.isNullOrBlank()) root.icon(icon)
                // Buckets only exist when they have members.
                val firstUrl = bucket.items.first().urlText(get)
                if (firstUrl.isNotEmpty()) root.url(firstUrl)
                for (member in bucket.items) root.child(member)
                roots.add(root)
            }
        }
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
    sideMode: SideNavMode = SideNavMode.STATIC,
): String {
    // Top bar (`inline`) turns each group into a dropdown trigger. The mobile drawer forces
    // every group into a collapsible accordion, whatever the desktop heading style is.
    // SIDE [SideNavMode.ACCORDION] does the same on the desktop tree. STATIC keeps headings.
    if (items.none { it.groupName() != null }) {
        // Inline top bar: nested links are dropdowns. Drawer and sidebar: nested lists.
        return renderItems(items, path, spa, get, nested, menu = inline)
    }
    val blocks = orderedBlocks(items, groups, get)
    if (inline) {
        val rows = StringBuilder()
        for (block in blocks) {
            when (block) {
                is NavBlock.Loose -> rows.append(renderItem(block.item, path, spa, get, nested, true))
                is NavBlock.Grouped -> rows.append(inlineGroup(block.bucket, path, spa, get, nested))
            }
        }
        return """<ul class="ff-nav-list">$rows</ul>"""
    }
    // Drawer keeps its own accordion. SIDE accordion collapses groups that miss the current page.
    val accordion = !drawer && sideMode == SideNavMode.ACCORDION
    val tree = !drawer
    return renderStacked(blocks, path, spa, get, nested, drawer, accordion, tree)
}

private fun inlineGroup(
    bucket: GroupBucket,
    path: String,
    spa: Boolean,
    get: Get,
    nested: Boolean,
): String {
    val inner = StringBuilder()
    for (item in bucket.items) inner.append(renderItem(item, path, spa, get, nested, true))
    val holdsActive = bucket.items.any { itemOrDescendantActive(it, path, get) }
    val active = if (holdsActive) " ff-active" else ""
    val label = escapeHtml(bucket.group.label)
    return """<li class="ff-nav-item ff-cluster ff-nav-group ff-nav-dropdown$active">""" +
        """<button type="button" class="ff-nav-dropdown-trigger" aria-haspopup="menu" aria-expanded="false">""" +
        """${bucket.group.iconHtml()}<span class="ff-nav-label">$label</span></button>""" +
        """<ul class="ff-nav-list" role="menu">$inner</ul></li>"""
}

private fun renderStacked(
    blocks: List<NavBlock>,
    path: String,
    spa: Boolean,
    get: Get,
    nested: Boolean,
    drawer: Boolean,
    accordion: Boolean,
    tree: Boolean,
): String {
    val out = StringBuilder()
    val pending = ArrayList<NavigationItem>()
    fun flush() {
        if (pending.isEmpty()) return
        out.append(renderItems(pending, path, spa, get, nested))
        pending.clear()
    }
    for (block in blocks) {
        when (block) {
            is NavBlock.Loose -> pending.add(block.item)
            is NavBlock.Grouped -> {
                flush()
                out.append(renderStackGroup(block.bucket, path, spa, get, nested, drawer, accordion, tree))
            }
        }
    }
    flush()
    return out.toString()
}

private fun renderStackGroup(
    bucket: GroupBucket,
    path: String,
    spa: Boolean,
    get: Get,
    nested: Boolean,
    drawer: Boolean,
    accordion: Boolean,
    tree: Boolean,
): String {
    val holdsActive = bucket.items.any { itemOrDescendantActive(it, path, get) }
    // Static SIDE colors the tree line only. Accordion and the drawer may highlight the parent.
    val highlightParent = holdsActive && (accordion || drawer)
    val activeClass = if (highlightParent) " ff-nav-group-active" else ""
    val listClass = if (!tree) {
        "ff-nav-list"
    } else if (holdsActive) {
        "ff-nav-list ff-nav-tree ff-nav-tree-active"
    } else {
        "ff-nav-list ff-nav-tree"
    }
    val list = renderItems(bucket.items, path, spa, get, nested, listClass = listClass)
    val heading = bucket.group.iconHtml() + """<span class="ff-nav-label">${escapeHtml(bucket.group.label)}</span>"""
    val name = escapeHtml(bucket.group.label)
    val details = drawer || accordion || bucket.group.isCollapsible()
    if (details) {
        val open = if (drawer || accordion) {
            if (holdsActive) " open" else ""
        } else if (!bucket.group.isCollapsed() || holdsActive) {
            " open"
        } else {
            ""
        }
        return """<details class="ff-nav-group ff-nav-group-collapsible$activeClass" data-ff-nav-group="$name"$open><summary class="ff-nav-group-label">$heading</summary>$list</details>"""
    }
    return """<section class="ff-nav-group$activeClass" data-ff-nav-group="$name"><h3 class="ff-nav-group-label">$heading</h3>$list</section>"""
}

internal fun itemOrDescendantActive(item: NavigationItem, path: String, get: Get): Boolean {
    if (item.isActive(path, get)) return true
    for (child in item.children) {
        if (itemOrDescendantActive(child, path, get)) return true
    }
    return false
}
