package app.models;

import org.konaframework.firefly.support.BrandDisplay;
import org.konaframework.firefly.support.PanelBranding;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** In-memory panel branding overrides for the Desk sample. */
public final class AppSettings {
    public static final class Snapshot {
        public final String name;
        public final String logo;
        public final String logoDark;
        public final String logoHeight;
        public final String brandDisplay;
        public final String copyright;
        public final String primaryColor;
        public final String accentColor;

        public Snapshot(
            String name,
            String logo,
            String logoDark,
            String logoHeight,
            String brandDisplay,
            String copyright,
            String primaryColor,
            String accentColor
        ) {
            this.name = name;
            this.logo = logo;
            this.logoDark = logoDark;
            this.logoHeight = logoHeight;
            this.brandDisplay = brandDisplay;
            this.copyright = copyright;
            this.primaryColor = primaryColor;
            this.accentColor = accentColor;
        }
    }

    private static final AtomicReference<Snapshot> CURRENT =
        new AtomicReference<>(new Snapshot(null, null, null, null, null, null, null, null));

    private AppSettings() {}

    public static Snapshot get() {
        return CURRENT.get();
    }

    public static Map<String, Object> toMap() {
        Snapshot s = get();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", s.name == null ? "" : s.name);
        out.put("logo", s.logo == null ? "" : s.logo);
        out.put("logo_dark", s.logoDark == null ? "" : s.logoDark);
        out.put("logo_height", s.logoHeight == null ? "" : s.logoHeight);
        out.put("brand_display", s.brandDisplay == null ? "both" : s.brandDisplay);
        out.put("copyright", s.copyright == null ? "" : s.copyright);
        out.put("primary_color", s.primaryColor == null ? "" : s.primaryColor);
        out.put("accent_color", s.accentColor == null ? "" : s.accentColor);
        return out;
    }

    public static Snapshot save(Map<String, String> input) {
        Snapshot next = new Snapshot(
            blankToNull(input.get("name")),
            blankToNull(input.get("logo")),
            blankToNull(input.get("logo_dark")),
            blankToNull(input.get("logo_height")),
            blankToNull(input.get("brand_display")),
            blankToNull(input.get("copyright")),
            blankToNull(input.get("primary_color")),
            blankToNull(input.get("accent_color"))
        );
        CURRENT.set(next);
        return next;
    }

    public static PanelBranding toPanelBranding() {
        Snapshot s = get();
        if (allBlank(s)) {
            return null;
        }
        BrandDisplay display = null;
        if (s.brandDisplay != null) {
            switch (s.brandDisplay.toLowerCase()) {
                case "logo" -> display = BrandDisplay.LOGO;
                case "name" -> display = BrandDisplay.NAME;
                case "both" -> display = BrandDisplay.BOTH;
                default -> { }
            }
        }
        return new PanelBranding(
            s.name,
            s.logo,
            s.logoDark,
            s.logoHeight,
            display,
            s.copyright,
            null,
            null,
            null,
            s.primaryColor,
            s.accentColor
        );
    }

    public static void reset() {
        CURRENT.set(new Snapshot(null, null, null, null, null, null, null, null));
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean allBlank(Snapshot s) {
        return blankToNull(s.name) == null
            && blankToNull(s.logo) == null
            && blankToNull(s.logoDark) == null
            && blankToNull(s.logoHeight) == null
            && blankToNull(s.brandDisplay) == null
            && blankToNull(s.copyright) == null
            && blankToNull(s.primaryColor) == null
            && blankToNull(s.accentColor) == null;
    }
}
