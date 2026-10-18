package org.konaframework.brew

import org.konaframework.http.HttpException

/** Base type for Brew runtime failures. */
open class BrewException @JvmOverloads constructor(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class ComponentNotFoundException(name: String) :
    BrewException("Unable to find Brew component: [$name]")

class MethodNotFoundException(method: String, component: String) :
    BrewException("Unable to call component method. Public method [$method] not found on component [$component]")

class DirectlyCallingLifecycleHooksNotAllowedException(method: String, component: String) :
    BrewException("Unable to call lifecycle method [$method] directly on component: [$component]")

class CannotCallComputedDirectlyException(method: String, component: String) :
    BrewException("Cannot call computed property method [$method] directly on component: [$component]")

class PublicPropertyNotFoundException(property: String, component: String) :
    BrewException("Unable to set component data. Public property [$property] not found on component: [$component]")

class CannotUpdateLockedPropertyException(property: String) :
    BrewException("Cannot update locked property: [$property]")

class EventHandlerDoesNotExistException(event: String) :
    BrewException("Unable to handle event [$event]: no matching listener on the component")

class MultipleRootElementsException(component: String) :
    BrewException(
        "Brew only supports one HTML element per component. Multiple root elements detected for component: [$component]",
    )

class RootTagMissingFromViewException(component: String) :
    BrewException("Brew encountered a missing root tag when trying to render component [$component]")

/** Snapshot checksum mismatch or malformed snapshot (HTTP 419, like Livewire's corrupt payload handling). */
class CorruptComponentPayloadException @JvmOverloads constructor(
    message: String = "Brew encountered corrupt data when trying to hydrate a component. " +
        "Ensure that the [name, id, data] of the Brew component wasn't tampered with between requests.",
) : HttpException(419, message)

/** Request payload exceeded a configured guard (HTTP 413 / 422). */
class PayloadGuardException(status: Int, message: String) : HttpException(status, message)

/** Client value cannot be coerced to the property's declared type (HTTP 419). */
class PropertyTypeMismatchException(property: String, type: String) :
    HttpException(419, "Cannot assign the given value to property [$property] of type [$type]")
