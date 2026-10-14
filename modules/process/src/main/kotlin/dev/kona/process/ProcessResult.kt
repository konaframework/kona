package dev.kona.process

data class ProcessResult(
    val exitCode: Int,
    val output: String,
    val errorOutput: String,
) {
    fun successful(): Boolean = exitCode == 0
}
