package org.konaframework.wire

import org.konaframework.http.HttpException

/** Base type for Wire runtime failures. */
open class WireException @JvmOverloads constructor(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class ComponentNotFoundException(name: String) :
    WireException("Unable to find Wire component: [$name]")

class MethodNotFoundException(method: String, component: String) :
    WireException("Unable to call component method. Public method [$method] not found on component [$component]")

class DirectlyCallingLifecycleHooksNotAllowedException(method: String, component: String) :
    WireException("Unable to call lifecycle method [$method] directly on component: [$component]")

class CannotCallComputedDirectlyException(method: String, component: String) :
    WireException("Cannot call computed property method [$method] directly on component: [$component]")

class PublicPropertyNotFoundException(property: String, component: String) :
    WireException("Unable to set component data. Public property [$property] not found on component: [$component]")

class CannotUpdateLockedPropertyException(property: String) :
    WireException("Cannot update locked property: [$property]")

class EventHandlerDoesNotExistException(event: String) :
    WireException("Unable to handle event [$event]: no matching listener on the component")

class MultipleRootElementsException(component: String) :
    WireException(
        "Wire only supports one HTML element per component. Multiple root elements detected for component: [$component]",
    )

class RootTagMissingFromViewException(component: String) :
    WireException("Wire encountered a missing root tag when trying to render component [$component]")

/** Snapshot checksum mismatch or malformed snapshot (HTTP 419, like Livewire's corrupt payload handling). */
class CorruptComponentPayloadException @JvmOverloads constructor(
    message: String = "Wire encountered corrupt data when trying to hydrate a component. " +
        "Ensure that the [name, id, data] of the Wire component wasn't tampered with between requests.",
) : HttpException(419, message)

/** Request payload exceeded a configured guard (HTTP 413 / 422). */
class PayloadGuardException(status: Int, message: String) : HttpException(status, message)

/** Client value cannot be coerced to the property's declared type (HTTP 419). */
class PropertyTypeMismatchException(property: String, type: String) :
    HttpException(419, "Cannot assign the given value to property [$property] of type [$type]")
