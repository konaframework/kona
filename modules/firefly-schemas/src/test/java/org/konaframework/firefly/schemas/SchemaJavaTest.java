package org.konaframework.firefly.schemas;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SchemaJavaTest {
    @AfterEach
    void clear() {
        Section.configureUsing(null);
    }

    @Test
    void javaChainsSchemaHelpers() {
        Section.configureUsing(section -> { section.aside(true); });
        NameField name = new NameField();
        name.liveDebounce(250).afterStateUpdated((state, old, get, set) -> set.set("slug", "ada"));
        name.label(get -> "From Java");
        Section details = Section.make("Details");
        details.label(get -> "Section");
        details.columns(get -> 2);
        Schema schema = Schema.make().components(
            details.schema(Text.make("Hello").content(get -> "Hello"), name)
        );
        String html = schema.render();
        assertTrue(html.contains("data-ff-aside=\"true\""));
        assertTrue(html.contains("Section"));
        assertTrue(html.contains(">Hello</"));
        assertTrue(html.contains("data-ff-columns=\"2\""));
        assertTrue(html.contains("wire:model.live.debounce.250ms"));
        schema.set("name", "Ada");
        assertEquals("ada", schema.get("slug"));
        new Markup();
        Grid.configureUsing(null);
        Flex.configureUsing(null);
        Fieldset.configureUsing(null);
        Tabs.configureUsing(null);
        Wizard.configureUsing(null);
        Callout.configureUsing(null);
        assertTrue(Grid.make(1).render(schema).contains("ff-grid"));
        assertTrue(Flex.make().render(schema).contains("ff-flex"));
        assertTrue(Fieldset.make("Legend").render(schema).contains("ff-fieldset"));
        assertTrue(Tab.make("One").render(schema).contains("ff-tab"));
        assertTrue(Tabs.make().render(schema).contains("ff-tabs"));
        assertTrue(Wizard.make().render(schema).contains("ff-wizard"));
        assertTrue(WizardStep.make("Step").render(schema).contains("ff-wizard-step"));
        assertTrue(Callout.make("Note").render(schema).contains("ff-callout"));
        assertTrue(EmptyState.make("None").render(schema).contains("ff-empty"));
        assertTrue(Image.make("https://example.test/a.png").render(schema).contains("ff-image"));
        assertTrue(SchemaIcon.make("user").render(schema).contains("ff-schema-icon"));
        assertTrue(UnorderedList.make("one").render(schema).contains("ff-list"));
    }

    public static class NameField extends Component {
        public NameField() {
            super("name");
        }
    }
}
