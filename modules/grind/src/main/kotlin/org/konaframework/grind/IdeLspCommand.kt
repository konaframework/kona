package org.konaframework.grind

/**
 * `grind ide:lsp` — prints how to run kona-lsp (no long-lived process).
 */
class IdeLspCommand {
    fun run(args: List<String>): Int {
        val (_, options) = parseOptions(args)
        val io = GrindIO.fromOptions(options)
        io.box(
            "kona-lsp",
            listOf(
                "Crema language server (stdio LSP)",
                "Build: ./gradlew :kona-lsp:jar",
                "Run:   java -jar modules/lsp/build/libs/kona-lsp-*.jar",
                "Or:    ./gradlew :kona-lsp:installDist && …/bin/kona-lsp",
                "VS Code: ide-support/vscode-crema",
                "Docs: /docs/language/",
            ),
        )
        return 0
    }
}
