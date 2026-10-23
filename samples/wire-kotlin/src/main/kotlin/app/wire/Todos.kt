package app.wire

import org.konaframework.wire.Component
import org.konaframework.wire.Computed

data class Todo(var title: String = "", var done: Boolean = false)

/** A class-based component; its markup lives in `views/wire/todos.crema.html`. */
class Todos : Component() {
    var draft = ""
    var items = mutableListOf(Todo("Grind the beans"), Todo("Steam the milk", done = true))

    fun add() {
        val title = draft.trim()
        if (title.isEmpty()) return
        items.add(Todo(title))
        draft = ""
    }

    fun remove(index: Int) {
        items.removeAt(index)
    }

    @Computed
    fun remaining(): Int = items.count { !it.done }
}
