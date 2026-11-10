package org.konaframework.firefly

import org.konaframework.auth.Hash
import org.konaframework.cadence.Cadence
import org.konaframework.cadence.CadenceException
import org.konaframework.firefly.actions.Action
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
        headerActions(Action.make("save").label("Save").submitForm("ff-form"))
    }

    override fun sectionClass(): String = "ff-page ff-profile"

    override fun fallbackTitle(): String = "Profile"

    /**
     * Copy the signed-in name and email into the mounted form. Page state is not applied by the
     * form host, so without this the inputs render empty.
     */
    override fun render(state: Map<String, Any?>): String {
        val form = mountedForm()
        if (form != null) {
            form.fill(
                mapOf(
                    "name" to stateText(state, "name"),
                    "email" to stateText(state, "email"),
                ),
            )
        }
        return withAlert(super.render(state), state["error"])
    }

    /** Default name / email / password fields. Override [formSchema] to replace. */
    protected open fun formSchema(): Schema =
        Schema().components(
            TextInput.make("name").label("Name").required(),
            TextInput.make("email").label("Email").email().required(),
            TextInput.make("current_password").label("Current password").password()
                .helperText("Required to set a new password."),
            TextInput.make("password").label("New password").password()
                .helperText("Leave blank to keep the current password."),
            TextInput.make("password_confirmation").label("Confirm password").password(),
        )

    /** Record state for the form (keys match field names). */
    open fun fill(guard: PanelGuard): Map<String, Any?> =
        linkedMapOf(
            "name" to (guard.name() ?: ""),
            "email" to (guard.email() ?: guard.id() ?: ""),
            "current_password" to "",
            "password" to "",
            "password_confirmation" to "",
        )

    /**
     * Persist profile changes for the signed-in account.
     * [MemoryPanelGuard] updates its in-memory user. A Cadence user with fillable `name` and
     * `email` (the auth user the panel already loads) is saved through that model. Password
     * changes use the existing guard / hasher check when `current_password` was submitted.
     */
    open fun save(guard: PanelGuard, input: Map<String, String>): String {
        if (!guard.check()) throw FireflyException("Not signed in")
        val name = (input["name"] ?: "").trim()
        val email = (input["email"] ?: "").trim()
        val password = input["password"] ?: ""
        val confirm = input["password_confirmation"] ?: ""
        if (name.isBlank()) throw FireflyException("Name is blank")
        if (email.isBlank()) throw FireflyException("Email is blank")
        val nextPassword = if (password.isBlank()) null else password
        if (password.isNotEmpty()) {
            if (password != confirm) {
                throw FireflyException("Password confirmation does not match")
            }
            if (input.containsKey("current_password")) {
                val current = input.getValue("current_password")
                if (current.isEmpty()) throw FireflyException("Current password is blank")
                if (!currentPasswordMatches(guard, current)) {
                    throw FireflyException("Current password is incorrect")
                }
            }
        }
        if (guard is MemoryPanelGuard) {
            val error = guard.updateProfile(name, email, nextPassword)
            if (error != null) throw FireflyException(error)
            if (nextPassword != null) refreshLogin(guard, email, nextPassword)
            return "Profile updated"
        }
        val user = guard.user()
        if (user is Cadence<*> && user.isFillable("name") && user.isFillable("email")) {
            if (nextPassword != null && !user.isFillable("password")) {
                throw FireflyException("This account cannot change its password")
            }
            val updates = linkedMapOf<String, Any?>("name" to name, "email" to email)
            if (nextPassword != null) updates["password"] = nextPassword
            try {
                user.fill(updates).save()
            } catch (e: CadenceException) {
                val detail = e.message
                throw FireflyException(if (detail.isNullOrBlank()) "Profile could not be saved" else detail)
            }
            if (nextPassword != null) refreshLogin(guard, email, nextPassword)
            return "Profile updated"
        }
        throw FireflyException(
            "Default ProfilePage can only save MemoryPanelGuard accounts; override ProfilePage.save for app Auth",
        )
    }

    private fun currentPasswordMatches(guard: PanelGuard, currentPassword: String): Boolean {
        val stored = guard.user()?.getAuthPassword()
        if (stored != null && Hash.isHashed(stored)) {
            return Hash.check(currentPassword, stored)
        }
        return guard.attempt(loginName(guard), currentPassword)
    }

    private fun loginName(guard: PanelGuard): String {
        val email = guard.email()
        if (!email.isNullOrBlank()) return email
        val id = guard.id()
        if (!id.isNullOrBlank()) return id
        return ""
    }

    /** Re-establish the session after a password change. No request is bound in direct calls. */
    private fun refreshLogin(guard: PanelGuard, email: String, password: String) {
        try {
            guard.attempt(email, password)
        } catch (_: IllegalStateException) {
        }
    }

    private fun stateText(state: Map<String, Any?>, key: String): String {
        val value = state[key] ?: return ""
        return value.toString()
    }

    private fun withAlert(body: String, error: Any?): String {
        val message = if (error == null) "" else error.toString().trim()
        if (message.isEmpty()) return body
        val alert = """<p class="ff-auth-alert" role="alert">${escapeHtml(message)}</p>"""
        val marker = """<div class="ff-page-content">"""
        val at = body.indexOf(marker)
        if (at < 0) return body
        return body.substring(0, at + marker.length) + alert + body.substring(at + marker.length)
    }
}
