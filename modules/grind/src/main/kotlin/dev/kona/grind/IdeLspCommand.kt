package dev.kona.grind

/**
 * `grind ide:lsp` — prints how to run kona-lsp (no long-lived process).
 */
class IdeLspCommand {
    fun run(args: List<String>): Int {
        val (_, options) = parseOptions(args)
        val io = GrindIO.fromOptions(options)
        io.writeln(
            """
            kona-lsp — Crema language server (stdio LSP)

            Build the jar:
              ./gradlew :kona-lsp:jar

            Run (editors speak LSP over stdin/stdout):
              java -jar modules/lsp/build/libs/kona-lsp-*.jar

            Or the application script:
              ./gradlew :kona-lsp:installDist
              modules/lsp/build/install/kona-lsp/bin/kona-lsp

            VS Code / Cursor: ide-support/vscode-crema (set crema.lsp.jarPath)
            IntelliJ: ide-support/intellij/README.md
            Docs: /docs/language/
            """.trimIndent(),
        )
        return 0
    }
}
