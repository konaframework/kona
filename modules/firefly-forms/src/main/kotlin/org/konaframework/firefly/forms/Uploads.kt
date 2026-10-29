package org.konaframework.firefly.forms

import org.konaframework.firefly.actions.Action
import org.konaframework.filesystem.FilesystemAdapter
import org.konaframework.filesystem.Visibility
import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException
import org.konaframework.http.Handler
import org.konaframework.http.Request
import org.konaframework.http.Response
import org.konaframework.http.Router
import org.konaframework.http.UploadedFile
import java.util.UUID
import java.util.function.Consumer
import java.util.function.Function

fun interface UploadDisks {
    fun disk(name: String): FilesystemAdapter
}

data class StoredUpload(val path: String, val originalName: String, val mime: String?)

object FireflyUploads {
    const val PATH: String = "/_firefly/uploads"
    const val TEMP_DIRECTORY: String = "firefly-temporary"

    @JvmStatic
    fun register(router: Router, disks: UploadDisks): Unit {
        register(router, disks, null)
    }

    @JvmStatic
    fun register(router: Router, disks: UploadDisks, csrf: Function<Request, Boolean>?): Unit {
        router.post(PATH, Handler { request -> receive(request, disks, csrf) })
    }

    @JvmStatic
    fun receive(request: Request, disks: UploadDisks, csrf: Function<Request, Boolean>?): Response {
        if (csrf != null && !csrf.apply(request)) {
            return Response(419, "CSRF token mismatch.")
        }
        val file = request.file("file")
        if (file == null || !file.isValid()) return Response(422, "Upload is missing.")
        val diskName = request.input("disk") ?: "local"
        if (!diskName.matches(Regex("[A-Za-z0-9_-]+"))) return Response(422, "Unknown disk.")
        val adapter = try {
            disks.disk(diskName)
        } catch (_: Exception) {
            return Response(422, "Unknown disk.")
        }
        val stored = store(file, adapter, null, false)
        return Response.json(mapOf("path" to stored.path, "name" to stored.originalName))
    }

    @JvmStatic
    fun store(file: UploadedFile, adapter: FilesystemAdapter, namer: Function<StoredUpload, String>?, preserve: Boolean): StoredUpload {
        val original = safeName(file.clientOriginalName)
        val extension = file.extension()
        val id = UUID.randomUUID().toString()
        val generated = if (extension.isEmpty()) id else "$id.$extension"
        val temp = "$TEMP_DIRECTORY/$generated"
        adapter.put(temp, file.contents)
        adapter.put("$temp.ffmeta", original)
        val draft = StoredUpload(temp, original, file.clientMimeType)
        val named = when {
            namer != null -> "$TEMP_DIRECTORY/${safeName(namer.apply(draft))}"
            preserve -> "$TEMP_DIRECTORY/$original"
            else -> temp
        }
        if (named != temp) {
            val moved = adapter.move(temp, named)
            if (!moved) throw FireflyException("Upload could not be stored")
            adapter.move("$temp.ffmeta", "$named.ffmeta")
        }
        return StoredUpload(named, original, file.clientMimeType)
    }

    fun safeName(original: String): String {
        val base = original.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = base.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_', '.').take(80)
        if (cleaned.isBlank()) return "file"
        return cleaned
    }
}

open class FileUpload protected constructor(name: String) : Field<FileUpload>(name) {
    private var diskName: String = "local"
    private var directoryName: String = ""
    private var visibilityName: String = Visibility.PRIVATE
    private var multipleFlag: Boolean = false
    private var maxFilesCount: Int? = null
    private var minFilesCount: Int? = null
    private val accepted = ArrayList<String>()
    private var maxSizeKb: Int? = null
    private var minSizeKb: Int? = null
    private var imageFlag: Boolean = false
    private var avatarFlag: Boolean = false
    private var imageEditorFlag: Boolean = false
    private val aspectRatios = ArrayList<String>()
    private var editorMode: String? = null
    private var editorFill: String? = null
    private var editorViewportWidth: Int? = null
    private var editorViewportHeight: Int? = null
    private var previewHeight: Int? = null
    private var resizeMode: String? = null
    private var resizeWidth: Int? = null
    private var resizeHeight: Int? = null
    private var cropRatio: String? = null
    private var resizeUpscale: Boolean = false
    private var exifFlag: Boolean = true
    private var downloadableFlag: Boolean = false
    private var openableFlag: Boolean = false
    private var previewableFlag: Boolean = true
    private var reorderableFlag: Boolean = false
    private var appendFlag: Boolean = false
    private var storeFlag: Boolean = true
    private var moveFlag: Boolean = false
    private var preserveFlag: Boolean = false
    private var tamperFlag: Boolean = true
    private var pasteFlag: Boolean = false
    private var fetchInfoFlag: Boolean = true
    private var maxParallel: Int = 1
    private var panelLayoutText: String = "integrated"
    private var panelAspect: String? = null
    private var uploadButtonPosition: String = "left"
    private var removeButtonPosition: String = "left"
    private var progressPosition: String = "right"
    private var loadingPosition: String = "right"
    private var uploadingMessageText: String = "Uploading"
    private var namesState: String? = null
    private var browseAction: Action? = null
    private var namer: Function<StoredUpload, String>? = null

    fun disk(name: String): FileUpload = apply { diskName = name }

    fun directory(name: String): FileUpload = apply { directoryName = name.trim('/') }

    fun visibility(value: String): FileUpload = apply { visibilityName = value }

    fun multiple(on: Boolean): FileUpload = apply { multipleFlag = on }

    fun maxFiles(count: Int): FileUpload = apply { maxFilesCount = count }

    fun minFiles(count: Int): FileUpload = apply { minFilesCount = count }

    fun acceptedFileTypes(vararg types: String): FileUpload = apply {
        accepted.clear()
        accepted.addAll(types)
    }

    fun maxSize(kilobytes: Int): FileUpload = apply { maxSizeKb = kilobytes }

    fun minSize(kilobytes: Int): FileUpload = apply { minSizeKb = kilobytes }

    fun image(): FileUpload = apply {
        imageFlag = true
        if (accepted.isEmpty()) accepted.add("image/*")
    }

    fun avatar(): FileUpload = apply {
        image()
        avatarFlag = true
        imageEditorFlag = true
        panelLayoutText = "compact"
        if (aspectRatios.isEmpty()) aspectRatios.add("1:1")
    }

    fun imageEditor(on: Boolean): FileUpload = apply { imageEditorFlag = on }

    fun imageEditorAspectRatios(vararg ratios: String): FileUpload = apply {
        aspectRatios.clear()
        aspectRatios.addAll(ratios)
    }

    fun imageEditorMode(mode: String): FileUpload = apply { editorMode = mode }

    fun imageEditorEmptyFillColor(color: String): FileUpload = apply { editorFill = color }

    fun imageEditorViewportWidth(width: Int): FileUpload = apply { editorViewportWidth = width }

    fun imageEditorViewportHeight(height: Int): FileUpload = apply { editorViewportHeight = height }

    fun imagePreviewHeight(height: Int): FileUpload = apply { previewHeight = height }

    fun imageResizeMode(mode: String): FileUpload = apply { resizeMode = mode }

    fun imageResizeTargetWidth(width: Int): FileUpload = apply { resizeWidth = width }

    fun imageResizeTargetHeight(height: Int): FileUpload = apply { resizeHeight = height }

    fun imageCropAspectRatio(ratio: String): FileUpload = apply { cropRatio = ratio }

    fun imageResizeUpscale(on: Boolean): FileUpload = apply { resizeUpscale = on }

    fun orientImagesFromExif(on: Boolean): FileUpload = apply { exifFlag = on }

    fun downloadable(on: Boolean): FileUpload = apply { downloadableFlag = on }

    fun openable(on: Boolean): FileUpload = apply { openableFlag = on }

    fun previewable(on: Boolean): FileUpload = apply { previewableFlag = on }

    fun reorderable(on: Boolean): FileUpload = apply { reorderableFlag = on }

    fun appendFiles(on: Boolean): FileUpload = apply { appendFlag = on }

    fun storeFiles(on: Boolean): FileUpload = apply { storeFlag = on }

    fun moveFiles(on: Boolean): FileUpload = apply { moveFlag = on }

    fun preserveFilenames(on: Boolean): FileUpload = apply { preserveFlag = on }

    fun preventFilePathTampering(on: Boolean): FileUpload = apply { tamperFlag = on }

    fun pasteable(on: Boolean): FileUpload = apply { pasteFlag = on }

    fun fetchFileInformation(on: Boolean): FileUpload = apply { fetchInfoFlag = on }

    fun maxParallelUploads(count: Int): FileUpload = apply { maxParallel = count }

    fun panelLayout(layout: String): FileUpload = apply { panelLayoutText = layout }

    fun panelAspectRatio(ratio: String): FileUpload = apply { panelAspect = ratio }

    fun uploadButtonPosition(position: String): FileUpload = apply { uploadButtonPosition = position }

    fun removeUploadedFileButtonPosition(position: String): FileUpload = apply { removeButtonPosition = position }

    fun uploadProgressIndicatorPosition(position: String): FileUpload = apply { progressPosition = position }

    fun loadingIndicatorPosition(position: String): FileUpload = apply { loadingPosition = position }

    fun uploadingMessage(message: String): FileUpload = apply { uploadingMessageText = message }

    fun storeFileNamesIn(statePath: String): FileUpload = apply { namesState = statePath }

    fun browseAction(action: Action): FileUpload = apply { browseAction = action }

    fun browse(adapter: FilesystemAdapter): List<String> {
        val listed = adapter.files(directoryName)
        val out = ArrayList<String>()
        for (path in listed) {
            if (path.endsWith(".ffmeta")) continue
            if (!accepts(path)) continue
            out.add(path)
        }
        return out
    }

    fun chooseBrowsed(schema: Schema, path: String): Any? {
        if (!accepts(path)) throw FireflyException("File path is not allowed [$path]")
        if (multipleFlag) {
            val next = pathsOf(schema.get(fieldPath())).toMutableList()
            if (!next.contains(path)) next.add(path)
            schema.set(fieldPath(), next, false)
            return next
        }
        schema.set(fieldPath(), path, false)
        return path
    }

    fun getUploadedFileNameForStorageUsing(provider: Function<StoredUpload, String>): FileUpload = apply { namer = provider }

    fun disk(): String = diskName

    fun directory(): String = directoryName

    fun accepts(path: String): Boolean {
        if (!tamperFlag) return true
        if (path.contains("..") || path.endsWith(".ffmeta") || path.startsWith("/")) return false
        val normal = path.trim('/')
        if (normal.startsWith("${FireflyUploads.TEMP_DIRECTORY}/")) return true
        val root = directoryName
        if (root.isEmpty()) return true
        return normal == root || normal.startsWith("$root/")
    }

    fun receive(file: UploadedFile, disks: UploadDisks): StoredUpload =
        FireflyUploads.store(file, disks.disk(diskName), namer, preserveFlag)

    fun commit(schema: Schema, disks: UploadDisks): Any? {
        val path = fieldPath()
        val current = schema.get(path)
        if (!storeFlag) return current
        val paths = pathsOf(current)
        val adapter = disks.disk(diskName)
        val stored = ArrayList<String>()
        val names = LinkedHashMap<String, String>()
        for (item in paths) {
            if (!accepts(item)) throw FireflyException("File path is not allowed [$item]")
            if (!adapter.exists(item)) throw FireflyException("Uploaded file is missing [$item]")
            val size = adapter.size(item)
            val max = maxSizeKb
            if (max != null && size > max * 1024L) throw FireflyException("Uploaded file is too large")
            val min = minSizeKb
            if (min != null && size < min * 1024L) throw FireflyException("Uploaded file is too small")
            if (!typeAllowed(adapter, item)) throw FireflyException("Uploaded file type is not accepted")
            val original = adapter.getString("$item.ffmeta") ?: item.substringAfterLast('/')
            val draft = StoredUpload(item, original, adapter.mimeType(item))
            val filename = when {
                namer != null -> FireflyUploads.safeName(namer!!.apply(draft))
                preserveFlag -> original
                else -> item.substringAfterLast('/')
            }
            val directory = directoryName
            val target = if (directory.isEmpty()) filename else "$directory/$filename"
            if (item != target) {
                val ok = if (moveFlag) adapter.move(item, target) else adapter.copy(item, target)
                if (!ok) throw FireflyException("Uploaded file could not be saved")
                if (moveFlag) adapter.delete("$item.ffmeta")
            }
            adapter.setVisibility(target, visibilityName)
            stored.add(target)
            names[target] = original
        }
        val minFiles = minFilesCount
        if (minFiles != null && stored.size < minFiles) throw FireflyException("Upload needs more files")
        val maxFiles = maxFilesCount
        if (maxFiles != null && stored.size > maxFiles) throw FireflyException("Upload has too many files")
        val next: Any? = if (multipleFlag) stored else stored.firstOrNull()
        schema.set(path, next, false)
        val namesPath = namesState
        if (namesPath != null) schema.set(namesPath, names, false)
        return next
    }

    override fun kind(): String = "file-upload"

    override fun extraErrors(schema: Schema): Map<String, List<String>> {
        val path = fieldPath()
        val value = schema.get(path)
        val paths = pathsOf(value)
        if (isRequired(schema) && paths.isEmpty()) return mapOf(path to listOf("The $path field is required."))
        if (tamperFlag) {
            for (item in paths) {
                if (!accepts(item)) return mapOf(path to listOf("The $path field has an invalid file path."))
            }
        }
        return emptyMap()
    }

    override fun control(schema: Schema, get: Get, path: String, value: Any?): String {
        val paths = pathsOf(value)
        return buildString {
            append("""<div class="ff-filepond" x-data="ffFilePond" data-ff-path="${esc(path)}" data-ff-upload="${FireflyUploads.PATH}"${attr("data-ff-disk", diskName)}${attr("data-ff-directory", directoryName)}${attr("data-ff-visibility", visibilityName)}${boolAttr("data-ff-multiple", multipleFlag)}${boolAttr("data-ff-image", imageFlag)}${boolAttr("data-ff-avatar", avatarFlag)}${boolAttr("data-ff-image-editor", imageEditorFlag)}${attr("data-ff-aspect", if (aspectRatios.isEmpty()) null else aspectRatios.joinToString(","))}${attr("data-ff-editor-mode", editorMode)}${attr("data-ff-editor-fill", editorFill)}${attr("data-ff-viewport-width", editorViewportWidth?.toString())}${attr("data-ff-viewport-height", editorViewportHeight?.toString())}${attr("data-ff-preview-height", previewHeight?.toString())}${attr("data-ff-resize-mode", resizeMode)}${attr("data-ff-resize-width", resizeWidth?.toString())}${attr("data-ff-resize-height", resizeHeight?.toString())}${attr("data-ff-crop", cropRatio)}${boolAttr("data-ff-upscale", resizeUpscale)}${boolAttr("data-ff-exif", exifFlag)}${boolAttr("data-ff-download", downloadableFlag)}${boolAttr("data-ff-open", openableFlag)}${boolAttr("data-ff-preview", previewableFlag)}${boolAttr("data-ff-reorder", reorderableFlag)}${boolAttr("data-ff-append", appendFlag)}${boolAttr("data-ff-store", storeFlag)}${boolAttr("data-ff-move", moveFlag)}${boolAttr("data-ff-preserve", preserveFlag)}${boolAttr("data-ff-tamper", tamperFlag)}${boolAttr("data-ff-paste", pasteFlag)}${boolAttr("data-ff-fetch", fetchInfoFlag)} data-ff-parallel="$maxParallel"${attr("data-ff-layout", panelLayoutText)}${attr("data-ff-panel-aspect", panelAspect)}${attr("data-ff-upload-position", uploadButtonPosition)}${attr("data-ff-remove-position", removeButtonPosition)}${attr("data-ff-progress-position", progressPosition)}${attr("data-ff-loading-position", loadingPosition)}${attr("data-ff-uploading", uploadingMessageText)}${attr("data-ff-accept", if (accepted.isEmpty()) null else accepted.joinToString(","))}${attr("data-ff-names", namesState)}>""")
            val stored = if (multipleFlag) FormJson.encode(paths) else paths.firstOrNull() ?: ""
            append("""<input type="hidden" name="${esc(path)}" value="${esc(stored)}" data-ff-upload-value />""")
            append("""<input class="filepond" type="file"${if (multipleFlag) " multiple" else ""}${attr("accept", if (accepted.isEmpty()) null else accepted.joinToString(","))}${extraInputMarkup(get)} />""")
            for (item in paths) append("""<span class="ff-file" data-ff-path="${esc(item)}"></span>""")
            val browse = browseAction
            if (browse != null) append(browse.render(schema))
            append("</div>")
        }
    }

    private fun pathsOf(raw: Any?): List<String> {
        val value = FormJson.decode(raw)
        if (value == null) return emptyList()
        if (value is Collection<*>) {
            val out = ArrayList<String>()
            for (item in value) {
                if (item == null) continue
                val text = item.toString()
                if (text.isNotEmpty()) out.add(text)
            }
            return out
        }
        val text = value.toString()
        if (text.isEmpty()) return emptyList()
        return listOf(text)
    }

    private fun typeAllowed(adapter: FilesystemAdapter, path: String): Boolean {
        if (accepted.isEmpty()) return true
        val mime = adapter.mimeType(path) ?: ""
        val extension = path.substringAfterLast('.', "")
        for (type in accepted) {
            if (type == "image/*" && (mime.startsWith("image/") || extension in IMAGE_EXTENSIONS)) return true
            if (type == mime) return true
            if (type.startsWith(".") && path.endsWith(type)) return true
            if (!type.contains("/") && !type.startsWith(".") && extension.equals(type, ignoreCase = true)) return true
        }
        return false
    }

    companion object {
        private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp")
        private val maker = Maker { name -> FileUpload(name) }

        @JvmStatic
        fun configureUsing(block: Consumer<FileUpload>?) = maker.configureUsing(block)

        @JvmStatic
        fun make(name: String): FileUpload = maker.make(name)
    }
}
