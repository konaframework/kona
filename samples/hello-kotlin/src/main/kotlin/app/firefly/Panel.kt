package app.firefly

import app.firefly.pages.AccountProfile
import app.models.AppSettings
import org.konaframework.core.Kona
import org.konaframework.firefly.support.BrandDisplay
import org.konaframework.firefly.support.PanelBranding

/** Desk panel. Pages, widgets, and resources are discovered from this directory. */
class Panel : org.konaframework.firefly.Panel() {
    init {
        id("desk")
        path("admin")
        brand("Desk")
        branding(
            PanelBranding(
                name = "Desk",
                logo = "/branding/kona-logo.svg",
                brandDisplay = BrandDisplay.BOTH,
                copyright = "© Desk",
                primaryColor = "#DF8723",
                accentColor = "#74453F",
            ),
        )
        resolveBrandingOverrides { AppSettings.toPanelBranding() }
        profilePage(AccountProfile())
        authenticate(false)
        discover(Kona.app().basePath("src/main/kotlin/app/firefly"))
    }
}
