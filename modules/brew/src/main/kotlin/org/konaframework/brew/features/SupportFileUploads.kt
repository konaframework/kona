package org.konaframework.brew.features

import org.konaframework.brew.lifecycle.ComponentHook
import org.konaframework.brew.uploads.TemporaryUploadedFile
import org.konaframework.brew.uploads.WithFileUploads
import org.konaframework.brew.hydration.Synth
import org.konaframework.brew.hydration.SynthContext
import java.lang.reflect.Type

/** Skips unless the component implements [WithFileUploads]; registers the upload synth. */
class SupportFileUploads : ComponentHook() {
    override fun skip(): Boolean = component !is WithFileUploads

    override fun mount(params: Map<String, Any?>, key: String?) {
        ensureSynth()
    }

    override fun hydrate(memo: Map<String, Any?>, context: org.konaframework.brew.lifecycle.ComponentContext) {
        ensureSynth()
    }

    private fun ensureSynth() {
        manager.synths.register(TemporaryUploadSynth(manager.uploadRoot()))
    }
}

/** Dehydrates [TemporaryUploadedFile] as a tuple with key `tmpfile`. */
class TemporaryUploadSynth(private val root: java.nio.file.Path) : Synth() {
    override val key: String = "tmpfile"

    override fun match(value: Any): Boolean = value is TemporaryUploadedFile

    override fun dehydrate(value: Any, context: SynthContext): Pair<Any?, Map<String, Any?>> {
        val file = value as TemporaryUploadedFile
        return file.toMeta() to emptyMap()
    }

    override fun hydrate(value: Any?, meta: Map<String, Any?>, context: SynthContext, type: Type?): Any? {
        @Suppress("UNCHECKED_CAST")
        val map = value as? Map<String, Any?> ?: return null
        return TemporaryUploadedFile.fromMeta(map, root)
    }
}
