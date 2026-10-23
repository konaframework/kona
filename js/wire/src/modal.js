/**
 * Render a server error page inside a dismissable dialog so the developer
 * can read it without losing the current page.
 */
export function showHtmlModal(html) {
    let page = document.createElement('html')

    page.innerHTML = html
    page.querySelectorAll('a').forEach(a => a.setAttribute('target', '_top'))

    let modal = document.getElementById('brew-error')

    if (modal) {
        modal.innerHTML = ''
    } else {
        modal = document.createElement('dialog')
        modal.id = 'brew-error'
        Object.assign(modal.style, { margin: '50px', width: 'calc(100% - 100px)', height: 'calc(100% - 100px)', borderRadius: '5px', padding: '0px' })
    }

    let iframe = document.createElement('iframe')

    Object.assign(iframe.style, { backgroundColor: '#17161A', borderRadius: '5px', width: '100%', height: '100%' })
    modal.appendChild(iframe)

    document.body.prepend(modal)
    document.body.style.overflow = 'hidden'

    iframe.contentWindow.document.open()
    iframe.contentWindow.document.write(page.outerHTML)
    iframe.contentWindow.document.close()

    modal.addEventListener('click', () => hideHtmlModal(modal))
    modal.addEventListener('close', () => {
        modal.outerHTML = ''
        document.body.style.overflow = 'visible'
    })

    modal.showModal()
    modal.focus()
    modal.blur()
}

export function hideHtmlModal(modal) {
    modal.close()
}
