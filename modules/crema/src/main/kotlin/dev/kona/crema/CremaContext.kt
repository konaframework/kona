package dev.kona.crema

class CremaContext(
    private val data: MutableMap<String, Any?> = linkedMapOf(),
    private val sections: MutableMap<String, SectionBuffer> = linkedMapOf(),
    private val stacks: MutableMap<String, MutableList<String>> = linkedMapOf(),
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
        CremaContext(LinkedHashMap(data).also { it.putAll(extra) }, sections, stacks)

    fun section(name: String): SectionBuffer = sections.getOrPut(name) { SectionBuffer() }

    fun push(name: String, content: String) {
        stacks.getOrPut(name) { mutableListOf() }.add(content)
    }

    fun prepend(name: String, content: String) {
        stacks.getOrPut(name) { mutableListOf() }.add(0, content)
    }

    fun stack(name: String): String = stacks.getOrPut(name) { mutableListOf() }.joinToString("")
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
