package org.konaframework.brew;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.konaframework.brew.features.InMemoryComputedCache;
import org.konaframework.brew.features.SupportComputed;
import org.konaframework.brew.hydration.Coercion;
import org.konaframework.brew.hydration.Synth;
import org.konaframework.brew.hydration.Synthesizers;
import org.konaframework.brew.meta.ComponentMeta;
import org.konaframework.brew.testing.Testable;
import org.konaframework.core.Config;
import org.konaframework.crema.CremaEngine;
import org.konaframework.crema.MapViewFinder;
import org.konaframework.http.Router;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Java-callable surface: static helpers, {@code @JvmOverloads} variants and fluent testing. */
class JavaApiTest {
    public static class Clicker extends Component {
        public int clicks = 0;
        public String label = "go";

        public void click() {
            clicks++;
        }

        public void hush() {
            skipRender();
        }

        @Computed
        public String shout() {
            return label.toUpperCase();
        }

        @Override
        public String template() {
            return "<button>{{ label }} {{ clicks }} a b c</button>";
        }
    }

    @Test
    void javaOverloadsAndStatics() {
        BrewManager brew = new BrewManager(new BrewConfig("k"), new CremaEngine(new MapViewFinder()));
        brew.component("clicker", Clicker.class);
        Component fresh = brew.newComponent("clicker");
        assertTrue(fresh instanceof Clicker);

        Testable t = Testable.create(brew, "clicker", Map.of());
        t.call("click").assertNotSet("clicks", 0).assertSeeInOrder(List.of("a", "b", "c")).assertDontSee("zzz");
        t.call("hush").assertRenderSkipped();
        assertEquals("GO", SupportComputed.value(brew, t.instance(), "shout"));
        SupportComputed.forget(brew, t.instance(), "shout");

        assertSame(ComponentMeta.of(Clicker.class), ComponentMeta.of(t.instance()));
        assertEquals(20, BrewConfig.from(new Config()).getMaxComponents());
        BrewServiceProvider.routes(brew, new Router());

        assertEquals("raw", BrewView.raw("raw").getHtml());
        assertEquals("v", BrewView.named("v").getName());
        assertEquals("<p/>", BrewView.inline("<p/>").getSource());

        assertEquals("CreatePost", ComponentRegistry.studly("create-post"));
        assertEquals("create-post", ComponentRegistry.kebab("CreatePost"));
        assertEquals("clicker", ComponentRegistry.nameFor(Clicker.class, null));
        assertTrue(new ComponentRegistry().discover("org.konaframework.brew.fixtures.admin_area").contains("user-list"));

        new InMemoryComputedCache().put("a", 1, 1);
        assertEquals(5, Coercion.toType("5", int.class));
        assertNull(Synth.typeArgument(String.class, 0));
        assertEquals(String.class, Synth.rawClass(String.class));
        assertTrue(Synthesizers.isScalar("x"));
        assertFalse(Synthesizers.isTuple("x"));
    }

    @Test
    void cremaTemplateAnnotatesInlineSourceParameters() throws Exception {
        Target target = CremaTemplate.class.getAnnotation(Target.class);
        assertTrue(Arrays.asList(target.value()).contains(ElementType.PARAMETER));
        assertTrue(Arrays.asList(target.value()).contains(ElementType.METHOD));
        assertEquals(
                RetentionPolicy.SOURCE,
                CremaTemplate.class.getAnnotation(Retention.class).value());

        String component = readBrewSource("Component.kt");
        assertTrue(component.contains("@CremaTemplate"));
        assertTrue(component.contains("fun template()"));
        assertTrue(
                component.contains("fun inline(@CremaTemplate source: String"),
                component);
        String view = readBrewSource("BrewView.kt");
        assertTrue(
                view.contains("fun inline(@CremaTemplate source: String"),
                view);
    }

    private static String readBrewSource(String name) throws Exception {
        Path[] candidates = {
            Path.of("src/main/kotlin/org/konaframework/brew", name),
            Path.of("modules/brew/src/main/kotlin/org/konaframework/brew", name),
        };
        for (Path path : candidates) {
            if (Files.isRegularFile(path)) {
                return Files.readString(path);
            }
        }
        throw new AssertionError("missing Brew source " + name);
    }
}
