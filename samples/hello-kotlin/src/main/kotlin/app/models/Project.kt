package app.models

import org.konaframework.cadence.Cadence
import org.konaframework.cadence.CadenceCompanion
import org.konaframework.cadence.relations.HasMany

class Project : Cadence<Project>() {
    override val table = "projects"
    override val timestamps = false
    override val fillable = setOf("name", "active")

    fun tasks(): HasMany<Task> = hasMany(Task::class.java)

    companion object : CadenceCompanion<Project>(Project::class.java)
}
