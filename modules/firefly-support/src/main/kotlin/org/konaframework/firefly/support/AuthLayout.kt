package org.konaframework.firefly.support

/**
 * Centered guest document for login, register, password reset, and email verify.
 * Authenticated screens stay on [DefaultLayout]; auth never mounts the panel chrome.
 */
class AuthLayout : PanelLayout {
    override fun render(context: ChromeContext): String {
        val brand = context.brandName
        val title = context.documentTitle.ifBlank { brand }
        val body = context.partials["body"] ?: throw FireflyException("Missing partial [body]")
        val csrf = context.csrfToken
        val meta =
            if (csrf.isNullOrBlank()) {
                ""
            } else {
                """<meta name="csrf-token" content="${escAttr(csrf)}" />"""
            }
        val heading = context.theme.tokens().heading
        return """
            <!DOCTYPE html>
            <html lang="en">
              <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1" />
                $meta
                <title>${escAttr(title)}</title>
                <link rel="preconnect" href="https://fonts.googleapis.com" />
                <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
                <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=DM+Sans:ital,opsz,wght@0,9..40,400..700;1,9..40,400..700&family=Inter:ital,opsz,wght@0,400..700;1,400..700&display=swap" />
                ${DefaultLayout.THEME_INIT_SCRIPT}
                <style>${context.css}</style>
              </head>
              <body class="ff-auth" style="margin:0;background:${escAttr(context.theme.tokens().bodyBackground)};color:${escAttr(heading)}">
                <div class="ff-auth-shell">
                  <div class="ff-auth-atmosphere" aria-hidden="true"></div>
                  <div class="ff-auth-frame">
                    <header class="ff-auth-brand">
                      <p class="ff-auth-brand__name">${escAttr(brand)}</p>
                    </header>
                    $body
                  </div>
                </div>
              </body>
            </html>
        """.trimIndent()
    }

    private fun escAttr(value: String): String = buildString {
        for (ch in value) {
            when (ch) {
                '&' -> append("&amp;")
                '"' -> append("&quot;")
                '<' -> append("&lt;")
                else -> append(ch)
            }
        }
    }
}
