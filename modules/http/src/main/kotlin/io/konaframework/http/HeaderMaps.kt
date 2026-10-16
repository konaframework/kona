package io.konaframework.http

internal object HeaderMaps {
    fun from(headers: Map<String, List<String>>): Map<String, String> {
        val out = linkedMapOf<String, String>()
        for ((key, values) in headers) {
            out[key] = values.first()
        }
        return out
    }
}
