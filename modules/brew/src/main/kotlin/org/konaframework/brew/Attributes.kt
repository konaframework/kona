package org.konaframework.brew

/**
 * Property cannot be changed by the browser (`#[Locked]`). Kotlin: `@Locked var id = 0`; Java: annotate the public field.
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Locked

/**
 * Memoized derived value (`#[Computed]`). Put on a zero-argument public method; templates read it as `$name` or `$this.name`.
 *
 * - [persist]: cache across requests for this component instance (in the Brew cache store).
 * - [cache]: cache across **all** instances of the component class.
 * - [seconds]: TTL for [persist] / [cache] (default 3600).
 * - [key]: explicit cache key for [cache].
 * - [tags]: cache tags (informational for tag-aware stores).
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Computed(
    val persist: Boolean = false,
    val cache: Boolean = false,
    val seconds: Int = 3600,
    val key: String = "",
    val tags: Array<String> = [],
)

/** The action never re-renders the component (`#[Renderless]`). */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Renderless

/**
 * Registers a component class under an explicit name for discovery (`Brew.discover`).
 * Without it the name is derived from the class name (`ShowPosts` → `show-posts`).
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class BrewComponent(val value: String)

/**
 * Validation rules for a property (`#[Validate]`). Pipe-joined or array [value] uses Kona [org.konaframework.http.Validator] syntax.
 *
 * - [as]: attribute display name
 * - [message]: single custom message (applies to the first rule)
 * - [attribute]: alternate attribute key
 * - [onUpdate]: when false, skip real-time validation on `updated`
 * - [translate]: reserved for locales (M6); currently informational
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Validate(
    vararg val value: String = [],
    val `as`: String = "",
    val message: String = "",
    val attribute: String = "",
    val onUpdate: Boolean = true,
    val translate: Boolean = true,
)

/** Alias of [Validate] (`#[Rule]`). */
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Rule(
    vararg val value: String = [],
    val `as`: String = "",
    val message: String = "",
    val attribute: String = "",
    val onUpdate: Boolean = true,
    val translate: Boolean = true,
)

/**
 * CSS transition hints for morph updates (`#[Transition]`).
 * Client `wire:transition` reads [type]; [skip] opts out.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Transition(
    val type: String = "",
    val skip: Boolean = false,
)

/** Listen for a browser / server event (`#[On('post-created')]`). Dynamic `{prop}` placeholders are resolved from state. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Repeatable
@MustBeDocumented
annotation class On(vararg val value: String)

/** Run the action in parallel without blocking other requests (`#[Async]` / `.async`). */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Async

/** Expose a client-side JS function on `$wire.$js.name` (`#[Js]`). */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Js(val name: String = "")

/**
 * Action returns JSON to the caller and skips HTML render (`#[Json]`).
 * Validation failures reject the promise with the error bag.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Json

/** Prop is re-synced from the parent on every parent render (`#[Reactive]`). */
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Reactive

/** Prop is the child's `wire:model` binding target (`#[Modelable]`). */
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Modelable

/** Isolate this component into its own network request (`#[Isolate]`). */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Isolate

/** Lazy-load the component when it enters the viewport (`#[Lazy]`). */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Lazy(val bundle: Boolean = false)

/** Defer loading until after the initial page paint (`#[Defer]`). */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Defer(val bundle: Boolean = false)

/** Layout view wrapping a full-page component (`#[Layout('layouts.app')]`). */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Layout(val value: String)

/** Document title for a full-page component (`#[Title('…')]`). */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Title(val value: String)

/**
 * Sync a property with the query string (`#[Url]`).
 * - [as]: query key (defaults to the property name)
 * - [except]: omit from the URL when the value equals this sentinel (empty string = never omit by sentinel)
 * - [keep]: keep the key even when empty
 * - [history]: push a history entry on change
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Url(
    val `as`: String = "",
    val except: String = "",
    val keep: Boolean = false,
    val history: Boolean = false,
)

/** Persist a property in the session (`#[Session]` / `#[Session(key: '…')]`). */
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Session(val key: String = "")

/**
 * Authorize an action through Kona Gate before it runs (`#[Authorize('update', 'post')]`).
 * [ability] is required; [arguments] are resolved from component properties when they match a property name,
 * otherwise treated as literal strings.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Authorize(
    val ability: String,
    vararg val arguments: String = [],
)
