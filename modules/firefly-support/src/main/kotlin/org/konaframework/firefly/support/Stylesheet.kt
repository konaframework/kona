package org.konaframework.firefly.support

object Stylesheet {
    internal fun readCss(path: String = DEFAULT_CSS): String {
        val stream = Stylesheet::class.java.getResourceAsStream(path)
        if (stream == null) throw FireflyException("Missing Firefly stylesheet")
        val text = stream.readAllBytes().toString(Charsets.UTF_8)
        stream.close()
        return text
    }

    private val componentCss: String by lazy {
        readCss() + "\n" + readCss(FILTERS_CSS) + "\n" + readCss(WIDGETS_CSS) + "\n" + readCss(RICH_EDITOR_CSS)
    }

    @JvmStatic
    fun render(theme: FireflyTheme): String {
        val extra = theme.extraCss().trim()
        if (extra.isNotEmpty() && UNSCOPED.containsMatchIn(extra)) {
            throw FireflyException("Theme [${theme.id()}] CSS must stay under .ff-panel")
        }
        val tokens = theme.tokens()
        val variables = """
            .ff-panel,
            .ff-auth {
              --ff-primary: ${tokens.primary};
              --ff-accent: ${tokens.accent};
              --ff-surface: ${tokens.surface};
              --ff-heading: ${tokens.heading};
              --ff-font: ${tokens.font};
              --ff-radius: ${tokens.radius};
              --ff-sidebar-width: ${tokens.sidebarWidth};
              --ff-topbar-height: ${tokens.topbarHeight};
              --ff-body: ${tokens.bodyBackground};
            }
            .ff-panel {
              background: var(--ff-surface);
              color: var(--ff-heading);
            }
            .ff-panel.ff-dark,
            .ff-auth.ff-dark,
            .ff-dark .ff-panel,
            .ff-dark .ff-auth {
              --ff-primary: ${tokens.darkPrimary};
              --ff-surface: ${tokens.darkSurface};
              --ff-heading: ${tokens.darkHeading};
              --ff-body: ${tokens.darkSurface};
            }
            .ff-panel.ff-dark,
            .ff-dark .ff-panel {
              background: var(--ff-surface);
              color: var(--ff-heading);
            }
        """.trimIndent()
        val extraBlock = if (extra.isEmpty()) "" else "\n.ff-panel {\n$extra\n}"
        return FireflyFonts.css() + "\n" + variables + "\n" + componentCss.trim() + extraBlock
    }

    private val UNSCOPED = Regex("(?m)^\\s*body\\s*\\{")
    private const val DEFAULT_CSS = "/org/konaframework/firefly/support/firefly.css"
    private const val FILTERS_CSS = "/org/konaframework/firefly/support/table-filters.css"
    private const val WIDGETS_CSS = "/org/konaframework/firefly/support/form-widgets.css"
    private const val RICH_EDITOR_CSS = "/org/konaframework/firefly/support/rich-editor/simple.css"
}
