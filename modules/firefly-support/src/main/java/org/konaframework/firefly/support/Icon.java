package org.konaframework.firefly.support;

public final class Icon {
    public final String path;

    private Icon(String path) {
        this.path = path;
    }

    public String svg() {
        return "<svg class=\"ff-icon\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" aria-hidden=\"true\"><path d=\""
            + HtmlKt.escapeHtml(path) + "\"/></svg>";
    }

    public static final Icon HOME = new Icon("M3 12l9-9 9 9");
    public static final Icon BARS = new Icon("M4 6h16M4 12h16M4 18h16");
    public static final Icon USER = new Icon("M12 12a4 4 0 100-8 4 4 0 000 8");
    /** Shamar boolean true mark (Heroicons check path). */
    public static final Icon CHECK = new Icon("M4.5 12.75l6 6 9-13.5");
    public static final Icon X = new Icon("M6 18L18 6M6 6l12 12");

    public static Icon fromName(String name) {
        String key = name.toLowerCase(java.util.Locale.ROOT);
        if (key.equals("home")) return HOME;
        if (key.equals("bars")) return BARS;
        if (key.equals("user")) return USER;
        if (key.equals("check")) return CHECK;
        if (key.equals("x")) return X;
        throw new FireflyException("Unknown icon [" + name + "]");
    }
}
