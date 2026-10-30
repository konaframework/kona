package org.konaframework.firefly

import org.konaframework.firefly.forms.TextInput
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException
import java.util.function.Predicate

/**
 * Singleton settings page for panels (branding, prefs, app config).
 * Same form host as other pages; defaults to a wide content column.
 */
open class SettingsPage @JvmOverloads constructor(slug: String? = "settings") : CustomPage(slug) {
    private var canSavePredicate: Predicate<PanelGuard>? = null

    init {
        maxWidth("7xl")
        icon("cog")
    }

    override fun sectionClass(): String = "ff-page ff-settings"

    override fun fallbackTitle(): String = "Settings"

    /** When false, Save is disabled / rejected (viewers stay read-only). Default: allow. */
    fun canSave(predicate: Predicate<PanelGuard>): SettingsPage = apply { canSavePredicate = predicate }

    fun canSave(guard: PanelGuard): Boolean {
        val predicate = canSavePredicate ?: return true
        return predicate.test(guard)
    }
}

/**
 * Account profile page at `/{prefix}/profile`. Subclass to extend fields;
 * register with [Panel.profilePage] or [Firefly.defaultProfilePage].
 */
open class ProfilePage : CustomPage("profile") {
    init {
        icon("user")
        maxWidth("xl")
        form(formSchema())
    }

    override fun sectionClass(): String = "ff-page ff-profile"

    override fun fallbackTitle(): String = "Profile"

    /** Default name / email / password fields. Override [formSchema] to replace. */
    protected open fun formSchema(): Schema =
        Schema().components(
            TextInput.make("name").label("Name").required(),
            TextInput.make("email").label("Email").email().required(),
            TextInput.make("password").label("New password").password()
                .helperText("Leave blank to keep the current password."),
            TextInput.make("password_confirmation").label("Confirm password").password(),
        )

    /** Record state for the form (keys match field names). */
    open fun fill(guard: PanelGuard): Map<String, Any?> =
        linkedMapOf(
            "name" to (guard.name() ?: ""),
            "email" to (guard.email() ?: guard.id() ?: ""),
            "password" to "",
            "password_confirmation" to "",
        )

    /**
     * Persist profile changes. Default updates [MemoryPanelGuard] accounts.
     * Apps using [AuthPanelGuard] should override and write through Auth/Cadence.
     */
    open fun save(guard: PanelGuard, input: Map<String, String>): String {
        val name = (input["name"] ?: "").trim()
        val email = (input["email"] ?: "").trim()
        val password = input["password"] ?: ""
        val confirm = input["password_confirmation"] ?: ""
        if (name.isBlank()) throw FireflyException("Name is blank")
        if (email.isBlank()) throw FireflyException("Email is blank")
        if (password.isNotEmpty()) {
            if (password != confirm) {
                throw FireflyException("Password confirmation does not match")
            }
        }
        if (guard !is MemoryPanelGuard) {
            throw FireflyException(
                "Default ProfilePage can only save MemoryPanelGuard accounts; override ProfilePage.save for app Auth",
            )
        }
        val nextPassword = if (password.isBlank()) null else password
        val error = guard.updateProfile(name, email, nextPassword)
        if (error != null) throw FireflyException(error)
        return "Profile updated"
    }
}
