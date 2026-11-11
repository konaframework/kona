package org.konaframework.firefly.support;

import org.konaframework.icons.Icons;

/**
 * Firefly icon facade over {@link Icons} (Heroicons). Prefer {@link Icons} directly in new code.
 * A short name such as {@code home} resolves to outline Heroicons ({@code heroicon-o-home}).
 * A registered stack prefix passes through ({@code heroicon-o-funnel}, {@code heroicon-s-home},
 * or {@code fas-home} after {@code Icons.add} with prefix {@code fas}). Unknown names still throw.
 */
public final class Icon {
    private final String resolvedName;

    private Icon(String resolvedName) {
        this.resolvedName = resolvedName;
    }

    /** Resolved icon name passed to {@link Icons#svg(String)}. */
    public String name() {
        return resolvedName;
    }

    public String svg() {
        // Width and height match the 16px CSS box so the 24 viewBox is not scaled off the pixel grid.
        return Icons.svg(resolvedName).className("ff-icon").attr("width", "16").attr("height", "16").toHtml();
    }

    public static final Icon HOME = new Icon("heroicon-o-home");
    public static final Icon BARS = new Icon("heroicon-o-bars-3");
    public static final Icon USER = new Icon("heroicon-o-user");
    public static final Icon CHECK = new Icon("heroicon-o-check");
    public static final Icon X = new Icon("heroicon-o-x-mark");
    public static final Icon TRASH = new Icon("heroicon-o-trash");
    public static final Icon ARROWS_POINTING_OUT = new Icon("heroicon-o-arrows-pointing-out");
    public static final Icon ARROWS_POINTING_IN = new Icon("heroicon-o-arrows-pointing-in");

    public static Icon fromName(String name) {
        String key = name.trim().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        if (Icons.usesRegisteredPrefix(key)) {
            return new Icon(key);
        }
        return switch (key) {
            case "home" -> HOME;
            case "bars", "bars-3" -> BARS;
            case "user" -> USER;
            case "check" -> CHECK;
            case "x", "x-mark" -> X;
            case "trash" -> TRASH;
            case "arrows-pointing-out" -> ARROWS_POINTING_OUT;
            case "arrows-pointing-in" -> ARROWS_POINTING_IN;
            default -> {
                String candidate = "heroicon-o-" + key;
                if (Icons.has(candidate)) {
                    yield new Icon(candidate);
                }
                throw new FireflyException("Unknown icon [" + name + "]");
            }
        };
    }
}
