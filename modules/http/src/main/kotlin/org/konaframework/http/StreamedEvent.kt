package org.konaframework.http

/**
 * Named Server-Sent Event payload (Laravel `StreamedEvent` shape).
 *
 * [data] may be a [String]/[Number], or any value [JsonSupport] can encode.
 */
data class StreamedEvent(
    val event: String,
    val data: Any?,
)
