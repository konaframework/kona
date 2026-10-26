package org.konaframework.firefly.infolists

import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate

class ImageEntry private constructor(name: String) : Entry<ImageEntry>(name) {
    private val diskValue = TextSetting()
    private val visibilityValue = TextSetting()
    private val heightValue = TextSetting()
    private val widthValue = TextSetting()
    private val altValue = TextSetting()
    private val defaultUrl = TextSetting()
    private val sizeValue = TextSetting()
    private val circularFlag = BoolSetting(false)
    private val squareFlag = BoolSetting(false)
    private val stackedFlag = BoolSetting(false)
    private val separateFlag = BoolSetting(false)
    private val remainingFlag = BoolSetting(false)
    private val checkFlag = BoolSetting(true)
    private val overlapValue = IntSetting()
    private val ringValue = TextSetting()
    private val limitValue = IntSetting()
    private val imgAttrs = MapSetting()

    override fun kind(): String = "image"

    fun disk(value: String): ImageEntry = apply { diskValue.set(value) }

    fun disk(value: Function<Get, String>): ImageEntry = apply { diskValue.set(value) }

    fun visibility(value: String): ImageEntry = apply { visibilityValue.set(value) }

    fun visibility(value: Function<Get, String>): ImageEntry = apply { visibilityValue.set(value) }

    fun imageHeight(value: Int): ImageEntry = imageHeight("${value}px")

    fun imageHeight(value: String): ImageEntry = apply { heightValue.set(value) }

    fun imageHeight(value: Function<Get, String>): ImageEntry = apply { heightValue.set(value) }

    fun height(value: Int): ImageEntry = imageHeight(value)

    fun height(value: String): ImageEntry = imageHeight(value)

    fun imageWidth(value: Int): ImageEntry = imageWidth("${value}px")

    fun imageWidth(value: String): ImageEntry = apply { widthValue.set(value) }

    fun imageWidth(value: Function<Get, String>): ImageEntry = apply { widthValue.set(value) }

    fun width(value: Int): ImageEntry = imageWidth(value)

    fun width(value: String): ImageEntry = imageWidth(value)

    fun imageSize(value: Int): ImageEntry = imageSize("${value}px")

    fun imageSize(value: String): ImageEntry = apply {
        heightValue.set(value)
        widthValue.set(value)
    }

    fun imageSize(value: Function<Get, String>): ImageEntry = apply {
        heightValue.set(value)
        widthValue.set(value)
    }

    fun size(value: Int): ImageEntry = imageSize(value)

    fun size(value: String): ImageEntry = imageSize(value)

    fun circular(on: Boolean): ImageEntry = apply { circularFlag.set(on) }

    fun circular(predicate: Predicate<Get>): ImageEntry = apply { circularFlag.set(predicate) }

    fun square(on: Boolean): ImageEntry = apply { squareFlag.set(on) }

    fun square(predicate: Predicate<Get>): ImageEntry = apply { squareFlag.set(predicate) }

    fun alt(value: String): ImageEntry = apply { altValue.set(value) }

    fun alt(value: Function<Get, String>): ImageEntry = apply { altValue.set(value) }

    fun defaultImageUrl(value: String): ImageEntry = apply { defaultUrl.set(value) }

    fun defaultImageUrl(value: Function<Get, String>): ImageEntry = apply { defaultUrl.set(value) }

    fun extraImgAttributes(values: Map<String, String>): ImageEntry = apply { imgAttrs.set(values) }

    fun extraImgAttributes(provider: Function<Get, Map<String, String>>): ImageEntry = apply { imgAttrs.set(provider) }

    fun stacked(on: Boolean): ImageEntry = apply { stackedFlag.set(on) }

    fun stacked(predicate: Predicate<Get>): ImageEntry = apply { stackedFlag.set(predicate) }

    fun overlap(value: Int): ImageEntry = apply { overlapValue.set(value) }

    fun overlap(provider: Function<Get, Int>): ImageEntry = apply { overlapValue.set(provider) }

    fun ring(value: Int): ImageEntry = ring(value.toString())

    fun ring(value: String): ImageEntry = apply { ringValue.set(value) }

    fun ring(provider: Function<Get, String>): ImageEntry = apply { ringValue.set(provider) }

    fun limit(value: Int): ImageEntry = apply { limitValue.set(value) }

    fun limit(provider: Function<Get, Int>): ImageEntry = apply { limitValue.set(provider) }

    fun limitedRemainingText(on: Boolean): ImageEntry = apply { remainingFlag.set(on) }

    fun limitedRemainingText(predicate: Predicate<Get>): ImageEntry = apply { remainingFlag.set(predicate) }

    fun limitedRemainingTextSeparate(on: Boolean): ImageEntry = apply { separateFlag.set(on) }

    fun limitedRemainingTextSeparate(predicate: Predicate<Get>): ImageEntry = apply { separateFlag.set(predicate) }

    fun limitedRemainingTextSize(value: String): ImageEntry = apply { sizeValue.set(sizeOf(value)) }

    fun limitedRemainingTextSize(value: Function<Get, String>): ImageEntry = apply {
        sizeValue.set(Function { get -> sizeOf(value.apply(get)) })
    }

    fun checkFileExistence(on: Boolean): ImageEntry = apply { checkFlag.set(on) }

    fun checkFileExistence(predicate: Predicate<Get>): ImageEntry = apply { checkFlag.set(predicate) }

    override fun renderEntry(schema: Schema, get: Get): String {
        val state = resolvedState(schema, get)
        val sources = sources(state, get)
        if (sources.isEmpty()) {
            return """<span class="ff-placeholder">${escapeHtml(placeholderValue.get(get) ?: "")}</span>"""
        }
        val limit = limitValue.get(get)
        val extra = if (limit != null && sources.size > limit) sources.size - limit else 0
        val shown = if (limit != null && extra > 0) sources.take(limit) else sources
        val height = heightValue.get(get)
        val width = widthValue.get(get)
        val alt = altValue.get(get) ?: ""
        val imgs = shown.joinToString("") { src ->
            val tag = """<img class="ff-image" src="${escapeHtml(src)}" alt="${escapeHtml(alt)}"${attr("height", height)}${attr("width", width)}${extraAttrs(imgAttrs.get(get))}>"""
            link(get, tag)
        }
        val more = if (extra > 0 && remainingFlag.get(get)) {
            """<span class="ff-more"${flag("data-ff-separate", separateFlag.get(get))}${attr("data-ff-size", sizeValue.get(get))}>+$extra</span>"""
        } else {
            ""
        }
        return """<div class="ff-images"${flag("data-ff-circular", circularFlag.get(get))}${flag("data-ff-square", squareFlag.get(get))}${flag("data-ff-stacked", stackedFlag.get(get))}${attr("data-ff-overlap", overlapValue.get(get)?.toString())}${attr("data-ff-ring", ringValue.get(get))}${attr("data-ff-disk", diskValue.get(get))}${attr("data-ff-visibility", visibilityValue.get(get))}${flag("data-ff-check-file", checkFlag.get(get))}>$imgs$more</div>"""
    }

    private fun sources(state: Any?, get: Get): List<String> {
        if (isBlank(state)) {
            val fallback = defaultUrl.get(get)
            if (fallback == null || fallback.isEmpty()) return emptyList()
            return listOf(fallback)
        }
        val items = if (state is Collection<*>) state.map { it } else listOf(state)
        val out = ArrayList<String>()
        for (item in items) {
            val text = if (item == null) "" else item.toString()
            if (text.isEmpty()) {
                val fallback = defaultUrl.get(get)
                if (fallback != null && fallback.isNotEmpty()) out.add(fallback)
            } else if (checkFlag.get(get) && text.isBlank()) {
                continue
            } else {
                out.add(text)
            }
        }
        return out
    }

    companion object {
        @JvmStatic
        fun make(name: String): ImageEntry {
            val entry = ImageEntry(name)
            finish(entry)
            return entry
        }

        @JvmStatic
        fun configureUsing(block: Consumer<ImageEntry>?) = configure(ImageEntry::class.java, block)
    }
}
