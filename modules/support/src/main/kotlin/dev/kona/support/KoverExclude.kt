package dev.kona.support

/** Marks thin wrappers whose Kotlin epilogues Kover cannot fully attribute. */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class KoverExclude
