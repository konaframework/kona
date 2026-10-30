package app.firefly.pages;

import org.konaframework.firefly.PanelGuard;
import org.konaframework.firefly.ProfilePage;
import org.konaframework.firefly.forms.TextInput;
import org.konaframework.firefly.schemas.Schema;

import java.util.LinkedHashMap;
import java.util.Map;

/** Sample ProfilePage extension with an extra phone field. */
public class AccountProfile extends ProfilePage {
    @Override
    protected Schema formSchema() {
        return new Schema().components(
            TextInput.make("name").label("Name").required(),
            TextInput.make("email").label("Email").email().required(),
            TextInput.make("phone").label("Phone"),
            TextInput.make("password").label("New password").password()
                .helperText("Leave blank to keep the current password."),
            TextInput.make("password_confirmation").label("Confirm password").password()
        );
    }

    @Override
    public Map<String, Object> fill(PanelGuard guard) {
        Map<String, Object> base = new LinkedHashMap<>(super.fill(guard));
        base.put("phone", "");
        return base;
    }
}
