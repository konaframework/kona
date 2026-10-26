package org.konaframework.firefly.forms;

import org.junit.jupiter.api.Test;
import org.konaframework.firefly.actions.Action;
import org.konaframework.firefly.schemas.Schema;
import org.konaframework.firefly.tables.MemoryRecordStore;
import org.konaframework.firefly.tables.Table;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FireflyFormsJavaTest {
    @Test
    void fieldsAreCallableFromJava() {
        TextInput name = TextInput.make("name")
            .label("Name")
            .label(get -> "Ada")
            .email()
            .required()
            .helperText("Work email");
        name.live();
        Select kind = Select.make("kind").options(Map.of("org", "Org")).useNative(false);
        DateTimePicker day = DatePicker.make("day").useNative(false).minDate("2024-03-01");
        FileUpload file = FileUpload.make("file").image().directory("photos");
        Schema schema = Schema.make().components(name, kind, day, file);
        schema.fill(Map.of("name", "a@b.co", "day", "2024-03-10"));
        String html = schema.render();
        assertTrue(html.contains("ff-combobox"));
        assertFalse(html.contains("<select"));
        assertTrue(html.contains("ff-picker"));
        assertFalse(html.contains("type=\"date\""));
        assertTrue(html.contains("ff-filepond"));
        assertTrue(html.contains("@entangle(name)"));
        assertEquals("2024-03-10", day.normalize(LocalDate.of(2024, 3, 10)));
        assertTrue(name.validationRules(schema).contains("email"));
        assertTrue(FieldValidation.validate(schema, java.util.List.of(name)).isEmpty());
        TextInput.configureUsing(null);
        Textarea.configureUsing(null);
        Textarea.make("body");
        Hidden.configureUsing(null);
        Hidden.make("token");
        TagsInput.configureUsing(null);
        TagsInput.make("tags");
        ColorPicker.configureUsing(null);
        ColorPicker.make("ink");
        Slider.configureUsing(null);
        Slider.make("level");
        KeyValue.configureUsing(null);
        KeyValue.make("meta");
        Placeholder.configureUsing(null);
        Placeholder.make("note");
        ViewField.configureUsing(null);
        ViewField.make("bio");
        Select.configureUsing(null);
        Checkbox.configureUsing(null);
        Checkbox.make("ok");
        Toggle.configureUsing(null);
        Toggle.make("on");
        CheckboxList.configureUsing(null);
        CheckboxList.make("roles");
        Radio.configureUsing(null);
        Radio.make("kind");
        ToggleButtons.configureUsing(null);
        ToggleButtons.make("mode");
        DateTimePicker.configureUsing(null);
        DateTimePicker.make("at");
        DateTimePicker.date("day");
        DateTimePicker.time("when");
        RichEditor.configureUsing(null);
        RichEditor.make("body");
        MarkdownEditor.configureUsing(null);
        MarkdownEditor.make("copy");
        CodeEditor.configureUsing(null);
        CodeEditor.make("script");
        assertTrue(CodeEditor.languages().contains("kotlin"));
        FileUpload.configureUsing(null);
        Repeater.configureUsing(null);
        Repeater.make("members");
        Builder.configureUsing(null);
        Builder.make("blocks");
        TextInput.make("chrome")
            .prefixAction(Action.make("prefix").label("Prefix"))
            .suffixAction(Action.make("suffix").label("Suffix"))
            .hintAction(Action.make("hint").label("Hint"));
        MemoryRecordStore store = new MemoryRecordStore();
        Select.make("author").relationship("authors", "name", store);
        Select.make("authorFiltered").relationship("authors", "name", store, null)
            .createOptionAction(Action.make("createAuthor").label("Create"));
        Repeater.make("lines")
            .addAction(Action.make("addLine").label("Add"))
            .deleteAction(Action.make("deleteLine").label("Delete"))
            .cloneAction(Action.make("cloneLine").label("Clone"))
            .extraItemActions(Action.make("extraLine").label("Extra"));
        Builder.make("layout").addBetweenAction(Action.make("between").label("Between"));
        RichEditor.make("article").editBlockAction("hero", Action.make("editHero").label("Edit"));
        FileUpload.make("photo").browseAction(Action.make("browse").label("Browse"));
        ModalTableSelect.configureUsing(null);
        ModalTableSelect.make("pick").table(Table.memory(java.util.List.of())).multiple(false);
        MorphToSelect.configureUsing(null);
        MorphToSelect.make("link").titleAttribute("name").type("posts", store);
    }
}
