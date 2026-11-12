package org.konaframework.firefly.support

/**
 * Centered guest document for login, register, password reset, and email verify.
 *
 * Phase B: markup lives in `views/firefly/auth/shell.crema.html`. This class packs
 * branding, the CSRF meta tag, and the body partial. Authenticated screens stay on
 * [DefaultLayout].
 */
class AuthLayout : PanelLayout {
    override fun render(context: ChromeContext): String {
        val body = context.partials["body"] ?: throw FireflyException("Missing partial [body]")
        val branding = resolvedBranding(context)
        val brand = branding.brandName
        val title = context.documentTitle.ifBlank { plainTextFromHtml(brand) }
        val tokens = context.theme.tokens()
        return FireflyCrema.render(
            FireflyCrema.AUTH_SHELL,
            mapOf(
                "documentTitle" to title,
                "csrfToken" to context.csrfToken?.takeUnless { it.isBlank() },
                "fontUrl" to branding.fontUrl?.takeUnless { it.isBlank() },
                "fontPreconnect" to branding.fontPreconnect,
                "themeInitScript" to DefaultLayout.THEME_INIT_SCRIPT,
                "css" to context.css,
                "bodyBackground" to tokens.bodyBackground,
                "bodyColor" to tokens.heading,
                "brandMark" to brandMarkHtml(branding, "ff-auth-brand__mark"),
                "body" to body,
                "copyright" to branding.copyrightText?.takeUnless { it.isBlank() },
            ),
        )
    }

    private fun resolvedBranding(context: ChromeContext): FireflyBranding {
        val branding = context.branding
        val name = if (context.brandName.isBlank()) branding.brandName else context.brandName
        if (name == branding.brandName) return branding
        return branding.copy(brandName = name)
    }
}
