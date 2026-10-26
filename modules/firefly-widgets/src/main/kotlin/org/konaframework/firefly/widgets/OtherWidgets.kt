package org.konaframework.firefly.widgets

import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.tables.Table
import java.util.function.Function

open class TableWidget protected constructor(name: String) : Widget(name) {
    private var table: Table<*, *>? = null

    fun table(value: Table<*, *>): TableWidget = apply { table = value }

    override fun extraAttributes(values: Map<String, String>): TableWidget {
        super.extraAttributes(values)
        return this
    }

    override fun renderBody(schema: Schema): String {
        val current = table
        if (current == null) throw FireflyException("Table widget [$name] has no table")
        return WidgetAssets.tag(WidgetScripts.WIDGET) + current.render()
    }

    companion object {
        @JvmStatic
        fun make(name: String): TableWidget = TableWidget(name)
    }
}

open class AccountWidget protected constructor(name: String) : Widget(name) {
    private val nameSetting = TextSetting()
    private val emailSetting = TextSetting()
    private val avatarSetting = TextSetting()

    fun accountName(value: String): AccountWidget = apply { nameSetting.setLiteral(value) }

    fun accountName(provider: Function<Get, String>): AccountWidget = apply { nameSetting.setUsing(provider) }

    fun email(value: String): AccountWidget = apply { emailSetting.setLiteral(value) }

    fun email(provider: Function<Get, String>): AccountWidget = apply { emailSetting.setUsing(provider) }

    fun avatar(value: String): AccountWidget = apply { avatarSetting.setLiteral(value) }

    fun avatar(provider: Function<Get, String>): AccountWidget = apply { avatarSetting.setUsing(provider) }

    override fun extraAttributes(values: Map<String, String>): AccountWidget {
        super.extraAttributes(values)
        return this
    }

    override fun renderBody(schema: Schema): String {
        val get = WidgetBridge.reader(schema, this)
        val account = nameSetting.resolve(get)
        if (account == null || account.isBlank()) throw FireflyException("Account widget [$name] has no name")
        val email = emailSetting.resolve(get)
        val avatar = avatarSetting.resolve(get)
        val emailHtml = if (email == null || email.isEmpty()) "" else """<p class="ff-account-email">${escapeHtml(email)}</p>"""
        val avatarHtml = if (avatar == null || avatar.isEmpty()) {
            ""
        } else {
            """<img class="ff-account-avatar" src="${escapeHtml(avatar)}" alt=""/>"""
        }
        return WidgetAssets.tag(WidgetScripts.WIDGET) + """<div class="ff-account">$avatarHtml<p class="ff-account-name">${escapeHtml(account)}</p>$emailHtml</div>"""
    }

    companion object {
        @JvmStatic
        fun make(name: String): AccountWidget = AccountWidget(name)
    }
}
