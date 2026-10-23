import { wireProperty } from '../wire.js'
import { fireAction as defaultFireAction } from '../request/index.js'

/** Injectable for Vitest; production uses the real request bus / fetch. */
export const uploadDeps = {
    fireAction: defaultFireAction,
}

export function uploadUri() {
    let base = (window.wireScriptConfig && window.wireScriptConfig.uri)
        || (window.brewScriptConfig && window.brewScriptConfig.uri)
        || '/wire/update'
    return base.replace(/\/update$/, '/upload')
}

export function fireUpload(name, detail) {
    document.dispatchEvent(new CustomEvent('wire-upload-' + name, { detail, bubbles: true }))
    document.dispatchEvent(new CustomEvent('brew-upload-' + name, { detail, bubbles: true }))
    document.dispatchEvent(new CustomEvent('livewire-upload-' + name, { detail, bubbles: true }))
}

let controllers = new Map()

export async function uploadFiles(component, property, files, { multiple = false } = {}) {
    let list = Array.from(files || [])
    if (list.length === 0) return
    let key = component.id + ':' + property
    let ctrl = new AbortController()
    controllers.set(key, ctrl)
    fireUpload('start', { property, component })
    try {
        let info = { name: list[0].name, size: list[0].size, type: list[0].type }
        let signed = await uploadDeps.fireAction(component, '_startUpload', [property, info, multiple])
        let uploadUrl = (signed && signed.uploadUrl) || uploadUri()
        let isS3 = !!(signed && signed.isS3)
        let paths = []
        for (let file of list) {
            let body = file.arrayBuffer ? await file.arrayBuffer() : await new Response(file).arrayBuffer()
            let doFetch = uploadDeps.fetch || fetch
            let response
            if (isS3) {
                let headers = Object.assign(
                    { 'Content-Type': file.type || 'application/octet-stream' },
                    (signed && signed.headers) || {},
                )
                response = await doFetch(uploadUrl, { method: 'PUT', headers, body, signal: ctrl.signal })
                if (! response.ok) throw new Error('upload failed')
                paths.push((signed && signed.path) || file.name)
            } else {
                let sep = uploadUrl.includes('?') ? '&' : '?'
                let url = uploadUrl + sep +
                    `name=${encodeURIComponent(property)}&filename=${encodeURIComponent(file.name)}&size=${file.size}&multiple=${multiple}`
                response = await doFetch(url, {
                    method: 'POST',
                    headers: { 'Content-Type': file.type || 'application/octet-stream', 'X-Brew': '1' },
                    body,
                    signal: ctrl.signal,
                })
                if (! response.ok) throw new Error('upload failed')
                let json = await response.json()
                paths.push(...(json.paths || []))
            }
            fireUpload('progress', { property, progress: 100, component })
        }
        await uploadDeps.fireAction(component, '_finishUpload', [property, paths, multiple])
        fireUpload('finish', { property, component })
    } catch (error) {
        await uploadDeps.fireAction(component, '_uploadErrored', [property, String(error), multiple]).catch(() => {})
        fireUpload('error', { property, error, component })
    } finally {
        controllers.delete(key)
    }
}

export function cancelUpload(component, property) {
    let ctrl = controllers.get(component.id + ':' + property)
    if (ctrl) ctrl.abort()
    fireUpload('cancel', { property, component })
}

export function registerUploads() {
    wireProperty('$upload', component => (property, file, options) =>
        uploadFiles(component, property, file ? [file] : [], { multiple: false, ...(options || {}) }))

    wireProperty('$uploadMultiple', component => (property, files, options) =>
        uploadFiles(component, property, files || [], { multiple: true, ...(options || {}) }))

    wireProperty('$cancelUpload', component => property => cancelUpload(component, property))

    wireProperty('$removeUpload', component => (property, filename) =>
        uploadDeps.fireAction(component, '_removeUpload', [property, filename]))
}
