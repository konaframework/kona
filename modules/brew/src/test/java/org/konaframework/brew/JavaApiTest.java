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
}
