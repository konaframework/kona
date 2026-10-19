package org.konaframework.brew

/**
 * Marks a Kotlin/Java string as inline Crema markup for IDE language injection.
 *
 * IntelliJ IDEA (with the Kona plugin) and editors that honor IntelliJ language
 * annotations treat the annotated string as Crema — the same role as Laravel's
 * `<<<'BLADE'` heredoc for Blade.
 *
 * Prefer a separate `views/brew/….crema.html` when the template is large; use
 * this for Brew single-file [Component.template] strings.
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
