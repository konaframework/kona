package dev.kona.cadence.relations

import dev.kona.cadence.Cadence
import dev.kona.cadence.CadenceException
import java.lang.reflect.InvocationTargetException
import java.util.function.Consumer

/** Optional constraint applied to a relation before it is eager loaded. */
typealias RelationConstraint = Consumer<Relation<*, *>>

/**
 * Loads named relations for a list of models with one query per relation (per nesting level).
 * Names are relation method names on the model (`posts` calls `posts()`); dots nest
 * (`posts.comments` loads each post's comments too).
 */
object EagerLoader {
    /** Loads [relations] onto [models]; returns [models]. */
    @JvmStatic
    fun <M : Cadence<*>> load(models: List<M>, vararg relations: String): List<M> =
        load(models, relations.associateWith { null })

    @JvmStatic
    fun <M : Cadence<*>> load(models: List<M>, relations: Map<String, RelationConstraint?>): List<M> {
        if (models.isEmpty()) {
            return models
        }
        topLevel(relations).forEach { (name, constraint) ->
            val relation = relationOf(models.first(), name)
            constraint?.accept(relation)
            val nested = relations.filterKeys { it.startsWith("$name.") }.mapKeys { it.key.removePrefix("$name.") }
            nested.forEach { (path, nestedConstraint) -> relation.query.with(path, nestedConstraint) }
            relation.eagerLoadOnto(models, name)
        }
        return models
    }

    /** First segments of every path, keeping the constraint given for the bare name. */
    private fun topLevel(relations: Map<String, RelationConstraint?>): Map<String, RelationConstraint?> {
        val result = linkedMapOf<String, RelationConstraint?>()
        relations.forEach { (path, constraint) ->
            val name = path.substringBefore('.')
            if (name == path) result[name] = constraint else result.putIfAbsent(name, null)
        }
        return result
    }

    /** Calls the public no-arg relation method [name] on [model]. */
    @JvmStatic
    fun relationOf(model: Cadence<*>, name: String): Relation<*, *> {
        val method = try {
            model.javaClass.getMethod(name)
        } catch (ex: NoSuchMethodException) {
            throw undefined(model, name)
        }
        method.isAccessible = true
        val value = try {
            method.invoke(model)
        } catch (ex: InvocationTargetException) {
            throw ex.targetException
        }
        return value as? Relation<*, *> ?: throw undefined(model, name)
    }

    private fun undefined(model: Cadence<*>, name: String) =
        CadenceException("Call to undefined relationship [$name] on model [${model.javaClass.name}].")
}
