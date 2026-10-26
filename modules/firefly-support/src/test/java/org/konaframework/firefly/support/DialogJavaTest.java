package org.konaframework.firefly.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DialogJavaTest {
    @AfterEach
    void clear() {
        Dialog.configureUsing(null);
    }

    @Test
    void javaCallsDialogAndShell() {
        Dialog.configureUsing(dialog -> dialog.width("md"));
        String html = Dialog.make("Java").slideOver().render();
        assertTrue(html.contains("ff-dialog-sidebar"));
        assertTrue(html.contains("ff-dialog-md"));

        PanelShell shell = new PanelShell().theme(new KonaTheme()).hook(Hook.BODY_START, "hi");
        assertTrue(shell.render().contains("hi"));
        assertEquals("kona", new KonaTheme().id());
        assertTrue(Icon.HOME.svg().contains("svg"));
        assertTrue(Icon.fromName("bars").svg().contains("ff-icon"));

        String themed = new PanelShell()
            .theme(new HarborTheme())
            .layout(new DarkLayout())
            .render();
        assertTrue(themed.contains("ff-panel ff-dark"));
        assertTrue(themed.contains("--ff-radius: 0.75rem"));
        assertTrue(themed.contains("box-shadow"));
    }
}

final class HarborTheme implements FireflyTheme {
    @Override public String id() { return "harbor"; }

    @Override public ThemeTokens tokens() {
        return new ThemeTokens(
            "#DF8723", "#74453F", "#FAF6F0", "#2A1C10",
            "ui-sans-serif, system-ui, sans-serif",
            "0.75rem", "18rem", "4rem", "#FAF6F0",
            "#C9A57B", "#1A120C", "#E8D4B8"
        );
    }

    @Override public String extraCss() {
        return ".ff-sidebar { box-shadow: inset -1px 0 0 var(--ff-accent); }";
    }
}

final class DarkLayout implements PanelLayout {
    @Override public String render(ChromeContext context) {
        return "<div class=\"ff-panel ff-dark\"><style>" + context.getCss() + "</style>"
            + context.getPartials().get("sidebar")
            + context.getPartials().get("topbar")
            + context.getPartials().get("body")
            + context.getPartials().get("footer")
            + "</div>";
    }
}
