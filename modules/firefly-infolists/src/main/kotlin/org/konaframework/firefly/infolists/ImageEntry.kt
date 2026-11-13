package org.konaframework.firefly.infolists

import org.konaframework.filesystem.FilesystemAdapter
import org.konaframework.filesystem.FilesystemManager
import org.konaframework.filesystem.Visibility
import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate

/** Opens a named filesystem disk for image entries. */
fun interface ImageDisks {
    fun open(name: String): FilesystemAdapter
}

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
    private val expiration = IntSetting()
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

    /**
     * Lifetime of a private-disk temporary URL, in seconds. The default is 300.
     * Zero or a negative value is rejected.
     */
    fun temporaryUrlExpiration(seconds: Int): ImageEntry = apply {
        if (seconds <= 0) throw FireflyException("Temporary URL lifetime must be positive")
        expiration.set(seconds)
    }

    fun temporaryUrlExpiration(provider: Function<Get, Int>): ImageEntry = apply { expiration.set(provider) }

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
                val resolved = resolveDisk(text, get)
                if (resolved != null) out.add(resolved)
            }
        }
        return out
    }

    /**
     * Storage paths become disk URLs when [useDisks] is set. `http`, `https`, `data`, and
     * protocol-relative values stay as written. A missing file is dropped, or replaced by
     * [defaultImageUrl], when [checkFileExistence] is on.
     */
    private fun resolveDisk(text: String, get: Get): String? {
        val rawDisk = diskValue.get(get)
        val diskName = if (rawDisk == null) null else rawDisk.trim().takeIf { it.isNotEmpty() }
        val opener = disks
        if (diskName == null || opener == null || isRemote(text)) return text
        val path = text.trim()
        if (path.isEmpty()) return text
        val adapter = opener.open(diskName)
        if (checkFlag.get(get) && !adapter.exists(path)) {
            val fallback = defaultUrl.get(get)
            if (fallback != null && fallback.isNotEmpty()) return fallback
            return null
        }
        return locate(adapter, diskName, path, get)
    }

    private fun locate(adapter: FilesystemAdapter, diskName: String, path: String, get: Get): String {
        val rawVisibility = visibilityValue.get(get)
        val visibility = if (rawVisibility == null) null else rawVisibility.trim().lowercase()
        val privateFile = when (visibility) {
            "private" -> true
            "public" -> false
            else -> adapter.getVisibility(path).equals(Visibility.PRIVATE, ignoreCase = true)
        }
        if (!privateFile) return adapter.url(path)
        val seconds = expiration.get(get) ?: DEFAULT_TEMPORARY_URL_SECONDS
        if (seconds <= 0) throw FireflyException("Temporary URL lifetime must be positive")
        return try {
            adapter.temporaryUrl(path, seconds.toLong())
        } catch (_: IllegalStateException) {
            throw FireflyException("Disk [$diskName] cannot sign a temporary URL for [$path]")
        }
    }

    companion object {
        private const val DEFAULT_TEMPORARY_URL_SECONDS: Int = 300

        private var disks: ImageDisks? = null

        /** Opens named disks while an image entry renders. Pass null to render paths unchanged. */
        @JvmStatic
        fun useDisks(opener: ImageDisks?) {
            disks = opener
        }

        /** Opens disks from a [FilesystemManager] while an image entry renders. */
        @JvmStatic
        fun useDisks(manager: FilesystemManager) = useDisks(ManagerDisks(manager))

        @JvmStatic
        fun clearDisks() {
            disks = null
        }

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

private class ManagerDisks(private val manager: FilesystemManager) : ImageDisks {
    override fun open(name: String): FilesystemAdapter = manager.disk(name)
}

private fun isRemote(text: String): Boolean {
    val lower = text.trim().lowercase()
    return lower.startsWith("https://") ||
        lower.startsWith("http://") ||
        lower.startsWith("data:") ||
        lower.startsWith("//")
}
