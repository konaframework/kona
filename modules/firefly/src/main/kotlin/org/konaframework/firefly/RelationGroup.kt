package org.konaframework.firefly

import org.konaframework.firefly.support.FireflyException

/**
 * Several [RelationManager]s under one tab on the owner edit / view page
 * (Filament `RelationGroup::make`). Nested tabs inside the group pick the active manager.
 */
class RelationGroup private constructor(
    private val labelText: String,
    private val managers: List<RelationManager>,
) {
    fun label(): String = labelText

    /** Managers in this group, in declaration order. */
    fun managers(): List<RelationManager> = managers

    companion object {
        /**
         * Group [managers] under a single top-level tab labeled [label].
         * At least one manager is required.
         */
        @JvmStatic
        fun make(label: String, vararg managers: RelationManager): RelationGroup {
            if (label.isBlank()) throw FireflyException("Relation group label is blank")
            if (managers.isEmpty()) throw FireflyException("Relation group [$label] has no managers")
            val names = HashSet<String>()
            for (manager in managers) {
                if (!names.add(manager.name())) {
                    throw FireflyException("Relation group [$label] repeats manager [${manager.name()}]")
                }
            }
            return RelationGroup(label.trim(), managers.toList())
        }
    }
}

/**
 * One top-level relation tab: a single manager or a [RelationGroup].
 * Used when rendering tabs; [Resource.relationManagers] still flattens every manager for POST routes.
 */
sealed class RelationEntry {
    abstract fun managers(): List<RelationManager>

    class Single internal constructor(val manager: RelationManager) : RelationEntry() {
        override fun managers(): List<RelationManager> = listOf(manager)
    }

    class Group internal constructor(val group: RelationGroup) : RelationEntry() {
        override fun managers(): List<RelationManager> = group.managers()
    }
}
