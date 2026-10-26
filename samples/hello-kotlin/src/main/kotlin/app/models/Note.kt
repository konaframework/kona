package app.models

import org.konaframework.cadence.Cadence

class Note : Cadence<Note>() {
    override val table = "notes"
    override val timestamps = false
    override val fillable = setOf("name", "status", "due")
}
