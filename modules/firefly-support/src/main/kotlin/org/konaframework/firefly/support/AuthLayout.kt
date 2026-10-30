package org.konaframework.firefly.support

/**
 * Centered guest document for login, register, password reset, and email verify.
 * Authenticated screens stay on [DefaultLayout]; auth never mounts the panel chrome.
 */
class AuthLayout : PanelLayout {
    override fun render(context: ChromeContext): String {
        val branding = run {
            val b = context.branding
            val name = if (context.brandName.isBlank()) b.brandName else context.brandName
            if (name == b.brandName) b else b.copy(brandName = name)
        }
        val brand = branding.brandName
        val title = context.documentTitle.ifBlank { brand }
        val body = context.partials["body"] ?: throw FireflyException("Missing partial [body]")
        val csrf = context.csrfToken
        val meta =
            if (csrf.isNullOrBlank()) {
                ""
            } else {
                """<meta name="csrf-token" content="${escAttr(csrf)}" />"""
            }
        val fontLink =
            if (branding.fontUrl.isNullOrBlank()) {
                ""
            } else {
                val pre =
                    if (branding.fontPreconnect) {
                        """<link rel="preconnect" href="https://fonts.googleapis.com" />
                <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />"""
                    } else {
                        ""
                    }
                """$pre
                <link rel="stylesheet" href="${escAttr(branding.fontUrl)}" />"""
            }
        val heading = context.theme.tokens().heading
        val mark = brandMarkHtml(branding, "ff-auth-brand__mark")
        val copyright =
            if (branding.copyrightText.isNullOrBlank()) {
                ""
            } else {
                """<p class="ff-auth-copyright">${escAttr(branding.copyrightText)}</p>"""
            }
        return """
            <!DOCTYPE html>
            <html lang="en">
              <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1" />
                $meta
                <title>${escAttr(title)}</title>
                $fontLink
                ${DefaultLayout.THEME_INIT_SCRIPT}
                <style>${context.css}</style>
              </head>
              <body class="ff-auth" style="margin:0;background:${escAttr(context.theme.tokens().bodyBackground)};color:${escAttr(heading)}">
                <div class="ff-auth-shell">
                  <div class="ff-auth-atmosphere" aria-hidden="true"></div>
                  <div class="ff-auth-frame">
                    <header class="ff-auth-brand">
                      $mark
                    </header>
                    $body
                    $copyright
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
