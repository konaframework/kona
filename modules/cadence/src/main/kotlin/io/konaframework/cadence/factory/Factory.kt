package io.konaframework.cadence.factory

import io.konaframework.cadence.Cadence
import java.util.function.Consumer
import java.util.function.Supplier
import java.util.function.UnaryOperator

/**
 * Builds [M] models from a [definition] map, for tests and seeders. Attributes are force-filled
 * (mass-assignment rules do not apply). Attribute values may be a [Supplier] (called per model) or
 * another [Factory] (its model is created and its key used — handy for `user_id`).
 *
 * ```kotlin
 * class UserFactory : Factory<User>(User::class.java) {
 *     override fun definition() = mapOf("name" to "User $index", "email" to "user$index@example.com")
 * }
 * UserFactory().count(3).createMany()
 * ```
 *
 * Builder calls ([count], [state], [sequence], callbacks) change this factory; create a new
 * factory for an unrelated batch.
 */
abstract class Factory<M : Cadence<M>>(val modelClass: Class<M>) {
    private var amount = 1
    private val states = mutableListOf<UnaryOperator<Map<String, Any?>>>()
    private var sequence: List<Map<String, Any?>> = emptyList()
    private val afterMaking = mutableListOf<Consumer<M>>()
    private val afterCreating = mutableListOf<Consumer<M>>()

    /** 0-based number of the model being built, counted over this factory's lifetime. */
    protected var index: Int = 0
        private set

    /** Default attributes for one model; read [index] to make values unique. */
    abstract fun definition(): Map<String, Any?>

    /** How many models [makeMany] / [createMany] build. */
    fun count(amount: Int): Factory<M> = apply {
        require(amount >= 0) { "Factory count must not be negative" }
        this.amount = amount
    }

    /** Overrides definition attributes for every model. */
    fun state(attributes: Map<String, Any?>): Factory<M> = state { it + attributes }

    /** Transforms the attributes of every model (receives definition plus earlier states). */
    fun state(transform: UnaryOperator<Map<String, Any?>>): Factory<M> = apply { states += transform }

    /** Cycles through [states], one per model: `sequence(mapOf("role" to "admin"), mapOf("role" to "user"))`. */
    fun sequence(vararg states: Map<String, Any?>): Factory<M> = apply { sequence = states.toList() }

    fun afterMaking(callback: Consumer<M>): Factory<M> = apply { afterMaking += callback }

    fun afterCreating(callback: Consumer<M>): Factory<M> = apply { afterCreating += callback }

    /** The attribute map for one model, without building it. */
    @JvmOverloads
    fun raw(overrides: Map<String, Any?> = emptyMap()): Map<String, Any?> {
        var attributes = definition()
        states.forEach { attributes = it.apply(attributes) }
        if (sequence.isNotEmpty()) {
            attributes = attributes + sequence[index % sequence.size]
        }
        val resolved = (attributes + overrides).mapValues { (_, value) -> resolve(value) }
        index++
        return resolved
    }

    /** One unsaved model. */
    @JvmOverloads
    fun make(overrides: Map<String, Any?> = emptyMap()): M {
        val model = Cadence.of(modelClass).newInstance().forceFill(raw(overrides))
        afterMaking.forEach { it.accept(model) }
        return model
    }

    /** [count] unsaved models. */
    @JvmOverloads
    fun makeMany(overrides: Map<String, Any?> = emptyMap()): List<M> = List(amount) { make(overrides) }

    /** One saved model. */
    @JvmOverloads
    fun create(overrides: Map<String, Any?> = emptyMap()): M {
        val model = make(overrides)
        model.save()
        afterCreating.forEach { it.accept(model) }
        return model
    }

    /** [count] saved models. */
    @JvmOverloads
    fun createMany(overrides: Map<String, Any?> = emptyMap()): List<M> = List(amount) { create(overrides) }

    private fun resolve(value: Any?): Any? = when (value) {
        is Factory<*> -> value.create().getKey()
        is Supplier<*> -> value.get()
        else -> value
    }

    companion object {
        /** A factory from a lambda, for Java or one-off use: `Factory.of(User.class, () -> Map.of("name", "Ada"))`. */
        @JvmStatic
        fun <M : Cadence<M>> of(modelClass: Class<M>, definition: Supplier<Map<String, Any?>>): Factory<M> =
            object : Factory<M>(modelClass) {
                override fun definition(): Map<String, Any?> = definition.get()
            }
    }
}
