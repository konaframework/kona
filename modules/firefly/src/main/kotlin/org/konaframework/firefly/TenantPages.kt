package org.konaframework.firefly

import org.konaframework.auth.Authenticatable
import org.konaframework.cadence.Cadence
import org.konaframework.firefly.forms.TextInput
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.tables.RecordStore
import org.konaframework.firefly.tables.RecordStores

/**
 * The page where a signed-in user creates a tenant, at `/{prefix}/new`
 * ([Panel.tenantRegistration]). The default form asks for a name and [register] saves the tenant
 * model with the posted fields. Override [register] to add the user to the new tenant:
 * call `super.register(input, user)`, attach the user, and return the tenant.
 */
open class TenantRegistration : CustomPage("new") {
    private var model: Class<*> = Any::class.java

    init {
        maxWidth("xl")
        navigationLabel("Register")
        form(formSchema())
    }

    override fun sectionClass(): String = "ff-page ff-tenant-registration"

    override fun fallbackTitle(): String = "Register"

    /** The registration fields. Their names fill the tenant model. */
    protected open fun formSchema(): Schema =
        Schema().components(TextInput.make("name").label("Name").required())

    internal fun bindModel(type: Class<*>) {
        model = type
    }

    /** Creates and returns the tenant from the posted [input]; [user] is the person registering it. */
    open fun register(input: Map<String, String>, user: Authenticatable?): Any {
        @Suppress("UNCHECKED_CAST")
        val store = RecordStores.open(model) as RecordStore<Any, Any?>
        val record = store.newRecord()
        store.fill(record, input)
        store.save(record)
        return record
    }
}

/**
 * The page where members edit the current tenant, at `/{prefix}/{tenant}/tenant-profile`
 * ([Panel.tenantProfile]). The default form edits the tenant's name. Override [formSchema],
 * [fill] and [save] for other fields.
 */
open class TenantProfile : CustomPage("tenant-profile") {
    init {
        maxWidth("xl")
        navigationLabel("Tenant profile")
        form(formSchema())
    }

    override fun sectionClass(): String = "ff-page ff-tenant-profile"

    override fun fallbackTitle(): String = "Tenant profile"

    protected open fun formSchema(): Schema =
        Schema().components(TextInput.make("name").label("Name").required())

    /** Record state for the form (keys match field names). */
    open fun fill(tenant: Cadence<*>): Map<String, Any?> = LinkedHashMap(tenant.getAttributes())

    /** Saves the posted [input] on [tenant] and returns the notification title. */
    open fun save(tenant: Cadence<*>, input: Map<String, String>): String {
        val name = input["name"].orEmpty()
        if (name.isBlank()) throw FireflyException("Name is blank")
        tenant.fill(input)
        tenant.save()
        return "Tenant profile updated"
    }
}
