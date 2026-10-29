package org.konaframework.firefly.support;

public final class Icon {
    public final String path;

    private Icon(String path) {
        this.path = path;
    }

    public String svg() {
        // stroke-width 2.25 + round caps match Shamar boolean-value SVGs (size via CSS).
        return "<svg class=\"ff-icon\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2.25\" aria-hidden=\"true\"><path stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\""
            + HtmlKt.escapeHtml(path) + "\"/></svg>";
    }

    public static final Icon HOME = new Icon("M3 12l9-9 9 9");
    public static final Icon BARS = new Icon("M4 6h16M4 12h16M4 18h16");
    public static final Icon USER = new Icon("M12 12a4 4 0 100-8 4 4 0 000 8");
    /** Shamar boolean true mark (Heroicons check path). */
    public static final Icon CHECK = new Icon("M4.5 12.75l6 6 9-13.5");
    public static final Icon X = new Icon("M6 18L18 6M6 6l12 12");
    /** Heroicons arrows-pointing-out — dialog maximize (#372). */
    public static final Icon ARROWS_POINTING_OUT = new Icon(
        "M3.75 3.75v4.5m0-4.5h4.5m-4.5 0L9 9M3.75 20.25v-4.5m0 4.5h4.5m-4.5 0L9 15M20.25 3.75h-4.5m4.5 0v4.5m0-4.5L15 9m5.25 11.25h-4.5m4.5 0v-4.5m0 4.5L15 15"
    );
    /** Heroicons arrows-pointing-in — dialog restore (#372). */
    public static final Icon ARROWS_POINTING_IN = new Icon(
        "M9 9V4.5M9 9H4.5M9 9L3.75 3.75M9 15v4.5M9 15H4.5M9 15l-5.25 5.25M15 9h4.5M15 9V4.5M15 9l5.25-5.25M15 15h4.5M15 15v4.5m0-4.5l5.25 5.25"
    );

    public static Icon fromName(String name) {
        String key = name.toLowerCase(java.util.Locale.ROOT);
        if (key.equals("home")) return HOME;
        if (key.equals("bars")) return BARS;
        if (key.equals("user")) return USER;
        if (key.equals("check")) return CHECK;
        if (key.equals("x")) return X;
        if (key.equals("arrows-pointing-out") || key.equals("arrows_pointing_out")) return ARROWS_POINTING_OUT;
        if (key.equals("arrows-pointing-in") || key.equals("arrows_pointing_in")) return ARROWS_POINTING_IN;
        throw new FireflyException("Unknown icon [" + name + "]");
    }
}
