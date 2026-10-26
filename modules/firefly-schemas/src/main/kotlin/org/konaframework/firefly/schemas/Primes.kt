package org.konaframework.firefly.schemas

import org.konaframework.firefly.support.Icon
import java.util.function.Function

class Text private constructor(private var body: String) : Component("") {
    private var bodyUsing: Function<Get, String>? = null
    private var badgeFlag: Boolean = false
    private var jsFlag: Boolean = false
    private var sizeName: String? = null
    private var sizeUsing: Function<Get, String>? = null

    override fun kind(): String = "text"

    fun content(value: String): Text = apply {
        body = value
        bodyUsing = null
    }

    fun content(provider: Function<Get, String>): Text = apply { bodyUsing = provider }

    fun badge(on: Boolean): Text = apply { badgeFlag = on }

    fun isBadge(): Boolean = badgeFlag

    fun js(): Text = apply { jsFlag = true }

    fun getContent(): String = body

    fun size(name: String): Text = apply {
        sizeName = name
        sizeUsing = null
    }

    fun size(provider: Function<Get, String>): Text = apply { sizeUsing = provider }

    fun getSize(): String? = sizeName

    override fun renderBody(schema: Schema): String {
        val get = schema.getFor(this)
        val textProvider = bodyUsing
        val text = if (textProvider != null) textProvider.apply(get) else body
        val sizeProvider = sizeUsing
        val size = if (sizeProvider != null) sizeProvider.apply(get) else sizeName
        val sizeAttr = if (size == null) "" else """ data-ff-size="${escapeHtml(size)}""""
        val badge = if (badgeFlag) """ data-ff-badge="true"""" else ""
        val js = if (jsFlag) """ data-ff-js="true"""" else ""
        return """<p class="ff-text"$sizeAttr$badge$js>${escapeHtml(text)}</p>"""
    }

    companion object {
        @JvmStatic
        fun make(content: String): Text = Component.finish(Text(content))
    }
}

class SchemaIcon private constructor(private val iconName: String) : Component("") {
    private var sizeName: String? = null
    private var sizeUsing: Function<Get, String>? = null

    override fun kind(): String = "icon"

    fun size(name: String): SchemaIcon = apply {
        sizeName = name
        sizeUsing = null
    }

    fun size(provider: Function<Get, String>): SchemaIcon = apply { sizeUsing = provider }

    fun getSize(): String? = sizeName

    override fun renderBody(schema: Schema): String {
        val provider = sizeUsing
        val size = if (provider != null) provider.apply(schema.getFor(this)) else sizeName
        val sizeAttr = if (size == null) "" else """ data-ff-size="${escapeHtml(size)}""""
        return """<span class="ff-schema-icon"$sizeAttr>${Icon.fromName(iconName).svg()}</span>"""
    }

    companion object {
        @JvmStatic
        fun make(name: String): SchemaIcon = Component.finish(SchemaIcon(name))
    }
}

class Image private constructor(private var imageUrl: String) : Component("") {
    private var urlUsing: Function<Get, String>? = null
    private var altText: String = ""
    private var altUsing: Function<Get, String>? = null
    private var height: String? = null
    private var heightUsing: Function<Get, String>? = null
    private var width: String? = null
    private var widthUsing: Function<Get, String>? = null

    override fun kind(): String = "image"

    fun url(value: String): Image = apply {
        imageUrl = value
        urlUsing = null
    }

    fun url(provider: Function<Get, String>): Image = apply { urlUsing = provider }

    fun getUrl(): String = imageUrl

    fun alt(value: String): Image = apply {
        altText = value
        altUsing = null
    }

    fun alt(provider: Function<Get, String>): Image = apply { altUsing = provider }

    fun getAlt(): String = altText

    fun imageHeight(value: String): Image = apply {
        height = value
        heightUsing = null
    }

    fun imageHeight(provider: Function<Get, String>): Image = apply { heightUsing = provider }

    fun imageWidth(value: String): Image = apply {
        width = value
        widthUsing = null
    }

    fun imageWidth(provider: Function<Get, String>): Image = apply { widthUsing = provider }

    fun imageSize(value: String): Image = apply {
        height = value
        width = value
        heightUsing = null
        widthUsing = null
    }

    fun getImageHeight(): String? = height

    fun getImageWidth(): String? = width

    override fun renderBody(schema: Schema): String {
        val get = schema.getFor(this)
        val urlProvider = urlUsing
        val src = if (urlProvider != null) urlProvider.apply(get) else imageUrl
        val altProvider = altUsing
        val altValue = if (altProvider != null) altProvider.apply(get) else altText
        val heightProvider = heightUsing
        val heightValue = if (heightProvider != null) heightProvider.apply(get) else height
        val widthProvider = widthUsing
        val widthValue = if (widthProvider != null) widthProvider.apply(get) else width
        val heightAttr = if (heightValue == null) "" else """ height="${escapeHtml(heightValue)}""""
        val widthAttr = if (widthValue == null) "" else """ width="${escapeHtml(widthValue)}""""
        return """<img class="ff-image" src="${escapeHtml(src)}" alt="${escapeHtml(altValue)}"$heightAttr$widthAttr>"""
    }

    companion object {
        @JvmStatic
        fun make(url: String): Image = Component.finish(Image(url))
    }
}

class UnorderedList private constructor() : Component("") {
    private val items = ArrayList<String>()
    private var itemsUsing: Function<Get, List<String>>? = null
    private var sizeName: String? = null
    private var sizeUsing: Function<Get, String>? = null

    override fun kind(): String = "list"

    fun items(vararg values: String): UnorderedList = apply {
        items.clear()
        items.addAll(values)
        itemsUsing = null
    }

    fun items(provider: Function<Get, List<String>>): UnorderedList = apply { itemsUsing = provider }

    fun size(name: String): UnorderedList = apply {
        sizeName = name
        sizeUsing = null
    }

    fun size(provider: Function<Get, String>): UnorderedList = apply { sizeUsing = provider }

    fun getSize(): String? = sizeName

    override fun renderBody(schema: Schema): String {
        val get = schema.getFor(this)
        val sizeProvider = sizeUsing
        val size = if (sizeProvider != null) sizeProvider.apply(get) else sizeName
        val sizeAttr = if (size == null) "" else """ data-ff-size="${escapeHtml(size)}""""
        val itemProvider = itemsUsing
        val rows = if (itemProvider != null) itemProvider.apply(get) else items
        val body = rows.joinToString("") { """<li>${escapeHtml(it)}</li>""" }
        return """<ul class="ff-list"$sizeAttr>$body</ul>"""
    }

    companion object {
        @JvmStatic
        fun make(vararg items: String): UnorderedList = Component.finish(UnorderedList().also { it.items(*items) })
    }
}
