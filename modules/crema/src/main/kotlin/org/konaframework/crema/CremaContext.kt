package org.konaframework.crema

class CremaContext(
    private val data: MutableMap<String, Any?> = linkedMapOf(),
    private val sections: MutableMap<String, SectionBuffer> = linkedMapOf(),
    private val stacks: MutableMap<String, MutableList<String>> = linkedMapOf(),
    private val onceKeys: MutableSet<String> = linkedSetOf(),
) {
    fun get(key: String): Any? = data[key]

    fun set(key: String, value: Any?): CremaContext {
        data[key] = value
        return this
    }

    fun merge(extra: Map<String, Any?>): CremaContext {
        data.putAll(extra)
        return this
    }

    fun snapshot(): Map<String, Any?> = LinkedHashMap(data)

    fun child(extra: Map<String, Any?> = emptyMap()): CremaContext =
        CremaContext(
            LinkedHashMap(data).also { it.putAll(extra) },
            sections,
            stacks,
            onceKeys,
        )

    fun section(name: String): SectionBuffer = sections.getOrPut(name) { SectionBuffer() }

    fun hasSection(name: String): Boolean = !section(name).isEmpty()

    /**
     * Marks a `@once` key. Returns true the first time this key is seen in the render.
     */
    fun markOnce(key: String): Boolean = onceKeys.add(key)

    fun push(name: String, content: String) {
        stacks.getOrPut(name) { mutableListOf() }.add(content)
    }

    fun prepend(name: String, content: String) {
        stacks.getOrPut(name) { mutableListOf() }.add(0, content)
    }

    fun stack(name: String): String = stacks.getOrPut(name) { mutableListOf() }.joinToString("")

    companion object {
        /**
         * Context flag: when truthy, conditional and loop blocks are wrapped in
         * `<!--[if BLOCK]><![endif]-->` … `<!--[if ENDBLOCK]><![endif]-->` so DOM morphing can
         * tell structural blocks apart (skipped when the block sits inside an HTML tag).
         */
        const val MORPH_MARKERS: String = "__crema_morph_markers"

        const val BLOCK_START: String = "<!--[if BLOCK]><![endif]-->"
        const val BLOCK_END: String = "<!--[if ENDBLOCK]><![endif]-->"
    }
}

class SectionBuffer {
    private val chunks = mutableListOf<String>()
    private var parentContent: String? = null

    fun append(content: String) {
        chunks += content
    }

    fun setParent(content: String) {
        parentContent = content
    }

    fun render(): String {
        val body = chunks.joinToString("")
        val parent = parentContent
        return if (parent != null && body.contains("@parent")) {
            body.replace("@parent", parent)
        } else if (parent != null && body.isBlank()) {
            parent
        } else {
            body
        }
    }

    fun isEmpty(): Boolean = chunks.isEmpty() && parentContent == null
}
