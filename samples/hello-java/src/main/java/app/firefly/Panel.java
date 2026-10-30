package app.firefly;

import app.firefly.pages.AccountProfile;
import app.models.AppSettings;
import org.konaframework.core.Kona;
import org.konaframework.firefly.support.BrandDisplay;
import org.konaframework.firefly.support.PanelBranding;

/** Desk panel. Pages, widgets, and resources are discovered from this directory. */
public class Panel extends org.konaframework.firefly.Panel {
    public Panel() {
        id("desk");
        path("admin");
        brand("Desk");
        branding(new PanelBranding(
            "Desk",
            "/branding/kona-logo.svg",
            null,
            null,
            BrandDisplay.BOTH,
            "© Desk",
            null,
            null,
            null,
            "#DF8723",
            "#74453F"
        ));
        resolveBrandingOverrides(ctx -> AppSettings.toPanelBranding());
        profilePage(new AccountProfile());
        authenticate(false);
        discover(Kona.app().basePath("src/main/java/app/firefly"));
    }
}
