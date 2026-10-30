package org.konaframework.firefly.forms

import org.konaframework.firefly.schemas.Get
import java.util.function.Function
import java.util.function.Predicate

internal class Phrase {
    private var literal: String? = null
    private var using: Function<Get, String>? = null

    fun set(value: String) {
        literal = value
        using = null
    }

    fun set(provider: Function<Get, String>) {
        using = provider
        literal = null
    }

    fun isUnset(): Boolean = literal == null && using == null

    fun get(get: Get): String? {
        val provider = using
        if (provider != null) return provider.apply(get)
        return literal
    }
}

internal class Switch {
    private var on: Boolean = false
    private var using: Predicate<Get>? = null

    fun set(value: Boolean) {
        on = value
        using = null
    }

    fun set(predicate: Predicate<Get>) {
        using = predicate
    }

    fun get(get: Get): Boolean {
        val predicate = using
        if (predicate != null) return predicate.test(get)
        return on
    }
}

internal class IntPhrase {
    private var literal: Int? = null
    private var using: Function<Get, Int>? = null

    fun set(value: Int) {
        literal = value
        using = null
    }

    fun set(provider: Function<Get, Int>) {
        using = provider
        literal = null
    }

    fun get(get: Get): Int? {
        val provider = using
        if (provider != null) return provider.apply(get)
        return literal
    }
}

internal class NumberPhrase {
    private var literal: Double? = null
    private var using: Function<Get, Double>? = null

    fun set(value: Double) {
        literal = value
        using = null
    }

    fun set(provider: Function<Get, Double>) {
        using = provider
        literal = null
    }

    fun get(get: Get): Double? {
        val provider = using
        if (provider != null) return provider.apply(get)
        return literal
    }
}

internal class Maker<T : Field<T>>(private val create: (String) -> T) {
    private var configurer: java.util.function.Consumer<T>? = null

    fun configureUsing(block: java.util.function.Consumer<T>?) {
        configurer = block
    }

    fun finish(field: T): T {
        org.konaframework.firefly.schemas.ComponentConfig.apply(field)
        val block = configurer
        if (block != null) block.accept(field)
        return field
    }

    fun make(name: String): T = finish(create(name))
}
