package app.firefly.pages

import org.konaframework.firefly.PanelGuard
import org.konaframework.firefly.ProfilePage
import org.konaframework.firefly.forms.TextInput
import org.konaframework.firefly.schemas.Schema

/** Sample ProfilePage extension — adds a phone field on top of the default account form. */
class AccountProfile : ProfilePage() {
    override fun formSchema(): Schema =
        Schema().components(
            TextInput.make("name").label("Name").required(),
            TextInput.make("email").label("Email").email().required(),
            TextInput.make("phone").label("Phone"),
            TextInput.make("password").label("New password").password()
                .helperText("Leave blank to keep the current password."),
            TextInput.make("password_confirmation").label("Confirm password").password(),
        )

    override fun fill(guard: PanelGuard): Map<String, Any?> =
        super.fill(guard) + ("phone" to "")
}
