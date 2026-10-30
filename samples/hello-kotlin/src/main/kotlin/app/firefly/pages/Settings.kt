package app.firefly.pages

import app.models.AppSettings
import org.konaframework.firefly.SettingsPage
import org.konaframework.firefly.forms.Select
import org.konaframework.firefly.forms.TextInput
import org.konaframework.firefly.schemas.Schema

/** Branding settings for the Desk panel (Shamar Settings Branding tab parity). */
class Settings : SettingsPage() {
    init {
        canSave { true }
        mountForm(
            Schema.make().components(
                TextInput.make("name").label("Brand name"),
                TextInput.make("logo").label("Logo URL"),
                TextInput.make("logo_dark").label("Dark logo URL"),
                TextInput.make("logo_height").label("Logo height"),
                Select.make("brand_display").label("Brand display").options(
                    mapOf(
                        "both" to "Logo and name",
                        "logo" to "Logo only",
                        "name" to "Name only",
                    ),
                ),
                TextInput.make("copyright").label("Copyright"),
                TextInput.make("primary_color").label("Primary color"),
                TextInput.make("accent_color").label("Accent color"),
            ),
        )
        onFormSubmit { input ->
            AppSettings.save(input)
            "Branding saved"
        }
    }

    override fun render(state: Map<String, Any?>): String {
        val filled = LinkedHashMap(state)
        filled.putAll(AppSettings.toMap())
        mountedForm()?.fill(filled)
        return super.render(filled)
    }
}
