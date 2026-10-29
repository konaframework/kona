package app.models

import org.konaframework.cadence.Cadence
import org.konaframework.cadence.CadenceCompanion
import org.konaframework.cadence.relations.BelongsTo

class Task : Cadence<Task>() {
    override val table = "tasks"
    override val timestamps = false
    override val fillable = setOf("project_id", "name", "status")

    fun project(): BelongsTo<Project> = belongsTo(Project::class.java, "project_id")

    companion object : CadenceCompanion<Task>(Task::class.java)
}
