package org.konaframework.wire

/**
 * Marks a Kotlin/Java string as inline Crema markup for IDE language injection.
 *
 * IntelliJ IDEA (with the Kona plugin) and editors that honor IntelliJ language
 * annotations treat the annotated string as Crema — the same role as Laravel's
 * `<<<'BLADE'` heredoc for Blade.
 *
 * Prefer a separate `views/wire/….crema.html` when the template is large; use
 * this for Wire single-file [Component.template] strings and [Component.inline] /
 * [WireView.inline] source arguments.
 *
 * Example:
 * ```kotlin
 * override fun template(): String = """
 *     <div wire:click="increment">{{ count }}</div>
 * """.trimIndent()
 * ```
 *
 * Or explicitly:
 * ```kotlin
 * // language=Crema
 * override fun template() = """…"""
 * ```
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
@MustBeDocumented
annotation class CremaTemplate
