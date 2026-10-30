package app.firefly.pages;

import app.models.AppSettings;
import org.konaframework.firefly.SettingsPage;
import org.konaframework.firefly.forms.Select;
import org.konaframework.firefly.forms.TextInput;
import org.konaframework.firefly.schemas.Schema;

import java.util.LinkedHashMap;
import java.util.Map;

/** Branding settings for the Desk panel. */
public class Settings extends SettingsPage {
    public Settings() {
        canSave(guard -> true);
        mountForm(Schema.make().components(
            TextInput.make("name").label("Brand name"),
            TextInput.make("logo").label("Logo URL"),
            TextInput.make("logo_dark").label("Dark logo URL"),
            TextInput.make("logo_height").label("Logo height"),
            Select.make("brand_display").label("Brand display").options(Map.of(
                "both", "Logo and name",
                "logo", "Logo only",
                "name", "Name only"
            )),
            TextInput.make("copyright").label("Copyright"),
            TextInput.make("primary_color").label("Primary color"),
            TextInput.make("accent_color").label("Accent color")
        ));
        onFormSubmit(input -> {
            AppSettings.save(input);
            return "Branding saved";
        });
    }

    @Override
    public String render(Map<String, ?> state) {
        Map<String, Object> filled = new LinkedHashMap<>(state);
        filled.putAll(AppSettings.toMap());
        Schema form = mountedForm();
        if (form != null) {
            form.fill(filled);
        }
        return super.render(filled);
    }
}
