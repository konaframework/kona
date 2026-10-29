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
    /** Heroicons trash — DeleteBulkAction trigger. */
    public static final Icon TRASH = new Icon(
        "M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0"
    );

    public static Icon fromName(String name) {
        String key = name.toLowerCase(java.util.Locale.ROOT);
        if (key.equals("home")) return HOME;
        if (key.equals("bars")) return BARS;
        if (key.equals("user")) return USER;
        if (key.equals("check")) return CHECK;
        if (key.equals("x")) return X;
        if (key.equals("trash")) return TRASH;
        if (key.equals("arrows-pointing-out") || key.equals("arrows_pointing_out")) return ARROWS_POINTING_OUT;
        if (key.equals("arrows-pointing-in") || key.equals("arrows_pointing_in")) return ARROWS_POINTING_IN;
        throw new FireflyException("Unknown icon [" + name + "]");
    }
}
