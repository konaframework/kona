package org.konaframework.brew.uploads

import org.konaframework.brew.Component
import org.konaframework.brew.Renderless
import org.konaframework.brew.meta.ComponentMeta

/**
 * Mixin for components that accept `wire:model` file uploads (`WithFileUploads`).
 * Implement on the component class; Brew registers the `_startUpload` / `_finishUpload` /
 * `_uploadErrored` / `_removeUpload` actions.
 */
interface WithFileUploads {
    /** Called when a temporary upload finished for [name] (property path). */
    fun finishUpload(name: String, tmpFilenames: List<String>, multiple: Boolean) {
        val component = this as Component
        val meta = ComponentMeta.of(component)
        val prop = meta.properties[name] ?: return
        val root = component.state().manager.uploadRoot()
        val files = tmpFilenames.map { path ->
            TemporaryUploadedFile(
                diskPath = path,
                originalName = path.substringAfterLast('/'),
                mimeType = "application/octet-stream",
                size = java.nio.file.Files.size(root.resolve(path)),
                root = root,
            )
        }
        when {
            prop.type.isArray -> prop.set(component, files.toTypedArray())
            multiple && List::class.java.isAssignableFrom(prop.type) -> prop.set(component, files)
            List::class.java.isAssignableFrom(prop.type) -> prop.set(component, files)
            else -> prop.set(component, files.firstOrNull())
        }
    }

    @Renderless
    fun _startUpload(name: String, fileInfo: Map<String, Any?>, isMultiple: Boolean): Map<String, Any?> {
        val component = this as Component
        val manager = component.state().manager
        if (manager.config.uploadDisk.equals("s3", ignoreCase = true)) {
            return manager.signS3Upload(name, fileInfo, isMultiple)
        }
        val signed = manager.signUpload(name, fileInfo, isMultiple)
        return linkedMapOf("uploadUrl" to signed.url, "token" to signed.token)
    }

    @Renderless
    fun _finishUpload(name: String, tmpFilenames: List<*>, isMultiple: Boolean) {
        finishUpload(name, tmpFilenames.map { it.toString() }, isMultiple)
    }

    @Renderless
    fun _uploadErrored(name: String, errorsInJson: String?, isMultiple: Boolean) {
        val component = this as Component
        component.addError(name, errorsInJson ?: "upload failed")
    }

    @Renderless
    fun _removeUpload(name: String, tmpFilename: String) {
        val component = this as Component
        val root = component.state().manager.uploadRoot()
        java.nio.file.Files.deleteIfExists(root.resolve(tmpFilename))
        val meta = ComponentMeta.of(component)
        val prop = meta.properties[name] ?: return
        when (val current = prop.get(component)) {
            is TemporaryUploadedFile -> if (current.diskPath == tmpFilename) prop.set(component, null)
            is List<*> -> prop.set(component, current.filterNot {
                it is TemporaryUploadedFile && it.diskPath == tmpFilename
            })
        }
    }
}

/** Signed upload endpoint details returned from `_startUpload`. */
data class SignedUpload(val url: String, val token: String)
