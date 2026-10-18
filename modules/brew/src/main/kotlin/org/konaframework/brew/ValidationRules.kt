package org.konaframework.brew

import org.konaframework.brew.meta.ComponentMeta

/** Collects `@Validate` / `@Rule` / `rules()` into a Validator rule map. */
internal object ValidationRules {
    fun forComponent(component: Component): Map<String, List<String>> {
        val fromMethod = component.rules()
        val fromAnnotations = linkedMapOf<String, MutableList<String>>()
        val onUpdate = mutableSetOf<String>()
        for ((name, prop) in ComponentMeta.of(component).properties) {
            for (ann in prop.annotations) {
                val parts: List<String>
                val update: Boolean
                when (ann) {
                    is Validate -> {
                        parts = ann.value.toList()
                        update = ann.onUpdate
                    }
                    is Rule -> {
                        parts = ann.value.toList()
                        update = ann.onUpdate
                    }
                    else -> continue
                }
                parts.flatMap { it.split('|') }.filter { it.isNotBlank() }
                    .forEach { fromAnnotations.getOrPut(name) { mutableListOf() }.add(it) }
                onUpdate += name
                if (!update) onUpdate.remove(name)
            }
        }
        component.state().store["brew.validateOnUpdate"] = onUpdate
        return fromAnnotations.mapValues { it.value.toList() } + fromMethod
    }

    fun shouldValidateOnUpdate(component: Component, property: String): Boolean {
        @Suppress("UNCHECKED_CAST")
        val cached = component.state().store["brew.validateOnUpdate"] as? Set<String>
        if (cached != null) return property in cached
        forComponent(component)
        @Suppress("UNCHECKED_CAST")
        return property in (component.state().store["brew.validateOnUpdate"] as Set<String>)
    }
}
