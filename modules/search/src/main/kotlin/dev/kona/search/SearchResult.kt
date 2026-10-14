package dev.kona.search

data class SearchResult(
    val hits: List<Hit>,
) {
    fun keys(): List<String> = hits.map { it.key }
    fun isEmpty(): Boolean = hits.isEmpty()
}

data class Hit(
    val key: String,
    val document: Map<String, Any?>,
    val score: Double = 1.0,
)
