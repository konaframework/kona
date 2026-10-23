import { interceptMessage } from '../request/index.js'

function downloadField(download, key, fallback) {
    return Object.prototype.hasOwnProperty.call(download, key) ? download[key] : fallback
}

/** Trigger a browser download from `effects.download`. */
export function registerDownloads() {
    interceptMessage(({ onSuccess }) => {
        onSuccess(({ payload }) => {
            let download = payload.effects && payload.effects.download

            if (! download) return

            let content = downloadField(download, 'content', '')
            let name = downloadField(download, 'name', 'download')
            let contentType = downloadField(download, 'contentType', 'application/octet-stream')
            let binary = atob(content)
            let bytes = new Uint8Array(binary.length)

            for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)

            let blob = new Blob([bytes], { type: contentType })
            let url = URL.createObjectURL(blob)
            let anchor = document.createElement('a')

            anchor.href = url
            anchor.download = name
            document.body.appendChild(anchor)
            anchor.click()
            anchor.remove()
            URL.revokeObjectURL(url)
        })
    })
}
