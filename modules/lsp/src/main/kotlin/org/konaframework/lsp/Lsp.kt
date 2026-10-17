package org.konaframework.lsp

import kotlin.system.exitProcess

/**
 * Process entry for `kona-lsp` (stdio Language Server Protocol).
 *
 * Run:
 * ```
 * java -jar kona-lsp-*.jar
 * ./gradlew :kona-lsp:run
 * ```
 */
fun main(args: Array<String>) {
    exitProcess(LspServer().serve(System.`in`, System.out))
}
