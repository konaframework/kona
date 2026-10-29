package app.models

import org.konaframework.cadence.Cadence
import org.konaframework.cadence.CadenceCompanion

class Note : Cadence<Note>() {
    override val table = "notes"
    override val timestamps = false
    override val fillable = setOf("name", "status", "due")

    companion object : CadenceCompanion<Note>(Note::class.java)
}
