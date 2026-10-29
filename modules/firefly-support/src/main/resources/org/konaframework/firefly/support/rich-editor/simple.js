/**
 * TipTap Simple Editor chrome — toolbar, icons, and popovers
 * aligned with https://tiptap.dev/docs/ui-components/templates/simple-editor
 */
import { Editor } from 'https://esm.sh/@tiptap/core@3.29.2'
import StarterKit from 'https://esm.sh/@tiptap/starter-kit@3.29.2'
import Underline from 'https://esm.sh/@tiptap/extension-underline@3.29.2'
import TextAlign from 'https://esm.sh/@tiptap/extension-text-align@3.29.2'
import Highlight from 'https://esm.sh/@tiptap/extension-highlight@3.29.2'
import Placeholder from 'https://esm.sh/@tiptap/extension-placeholder@3.29.2'
import Link from 'https://esm.sh/@tiptap/extension-link@3.29.2'
import Image from 'https://esm.sh/@tiptap/extension-image@3.29.2'
import TaskList from 'https://esm.sh/@tiptap/extension-task-list@3.29.2'
import TaskItem from 'https://esm.sh/@tiptap/extension-task-item@3.29.2'
import Superscript from 'https://esm.sh/@tiptap/extension-superscript@3.29.2'
import Subscript from 'https://esm.sh/@tiptap/extension-subscript@3.29.2'
import { icons } from './icons.js'

const HIGHLIGHT_COLORS = [
  { label: 'Gray', value: 'rgb(248, 248, 247)', border: 'rgba(84, 72, 49, 0.15)' },
  { label: 'Brown', value: 'rgb(244, 238, 238)', border: 'rgba(210, 162, 141, 0.35)' },
  { label: 'Orange', value: 'rgb(251, 236, 221)', border: 'rgba(224, 124, 57, 0.27)' },
  { label: 'Yellow', value: '#fef9c3', border: '#fbe604' },
  { label: 'Green', value: '#dcfce7', border: '#c7fad8' },
  { label: 'Blue', value: '#e0f2fe', border: '#ceeafd' },
  { label: 'Purple', value: '#f3e8ff', border: '#e4ccff' },
  { label: 'Pink', value: 'rgb(252, 241, 246)', border: 'rgba(225, 136, 179, 0.27)' },
  { label: 'Red', value: '#ffe4e6', border: '#ffccd0' },
]

function el(tag, attrs = {}, children = []) {
  const node = document.createElement(tag)
  for (const [k, v] of Object.entries(attrs)) {
    if (k === 'className') node.className = v
    else if (k === 'text') node.textContent = v
    else if (k === 'html') node.innerHTML = v
    else if (k === 'style' && v && typeof v === 'object') Object.assign(node.style, v)
    else if (v === true) node.setAttribute(k, '')
    else if (v === false || v == null) continue
    else node.setAttribute(k, String(v))
  }
  for (const child of children.flat().filter(Boolean)) {
    node.appendChild(typeof child === 'string' ? document.createTextNode(child) : child)
  }
  return node
}

function sep() {
  return el('span', { className: 'shamar-re-simple__sep', 'aria-hidden': 'true' })
}

function group(...children) {
  return el('div', { className: 'shamar-re-simple__group' }, children.flat().filter(Boolean))
}

function closePopovers(root) {
  root.querySelectorAll('.shamar-re-simple__popover').forEach((p) => {
    p.hidden = true
  })
  root.querySelectorAll('[data-state="open"]').forEach((n) => {
    n.setAttribute('data-state', 'closed')
  })
}

function placePopover(popover, anchor) {
  const rect = anchor.getBoundingClientRect()
  const pad = 8
  popover.hidden = false
  popover.style.position = 'fixed'
  popover.style.top = `${Math.round(rect.bottom + 6)}px`
  popover.style.left = '0'
  popover.style.zIndex = '1100'
  popover.style.visibility = 'hidden'
  const width = Math.max(popover.offsetWidth || 0, 200)
  let left = rect.left
  left = Math.max(pad, Math.min(left, window.innerWidth - width - pad))
  popover.style.left = `${Math.round(left)}px`
  popover.style.visibility = 'visible'
}

/**
 * @param {HTMLElement} root
 * @param {{
 *   initialHtml?: string,
 *   placeholder?: string,
 *   editable?: boolean,
 *   toolbar?: string[],
 *   onChange?: (html: string) => void,
 * }} [options]
 */
export function mountSimpleEditor(root, options = {}) {
  if (!root) throw new Error('mountSimpleEditor: root element required')
  const editable = options.editable !== false
  root.classList.add('shamar-re-simple')
  root.innerHTML = ''

  const toolbarEl = el('div', {
    className: 'shamar-re-simple__toolbar',
    role: 'toolbar',
    'aria-label': 'Formatting',
  })
  const toolbarScroll = el('div', { className: 'shamar-re-simple__toolbar-scroll' })
  const toolbarEnd = el('div', { className: 'shamar-re-simple__toolbar-end' })
  toolbarEl.append(toolbarScroll, toolbarEnd)

  const surface = el('div', { className: 'shamar-re-simple__surface' })
  const editorHost = el('div', { className: 'shamar-re-simple__editor' })
  surface.appendChild(editorHost)
  root.append(toolbarEl, surface)

  const editor = new Editor({
    element: editorHost,
    editable,
    extensions: [
      StarterKit.configure({
        heading: { levels: [1, 2, 3] },
        link: false,
      }),
      Underline,
      Superscript,
      Subscript,
      Highlight.configure({ multicolor: true }),
      TextAlign.configure({ types: ['heading', 'paragraph'] }),
      Link.configure({
        openOnClick: false,
        HTMLAttributes: { class: 'shamar-re-simple__link' },
      }),
      Image.configure({ HTMLAttributes: { class: 'shamar-re-simple__image' } }),
      TaskList,
      TaskItem.configure({ nested: true }),
      Placeholder.configure({
        placeholder: options.placeholder || 'Start writing…',
      }),
    ],
    content: (options.initialHtml || '').trim() || '<p></p>',
    editorProps: {
      attributes: {
        class: 'shamar-re-simple__prose simple-editor',
        spellcheck: 'true',
        autocomplete: 'off',
        autocorrect: 'off',
        autocapitalize: 'off',
        'aria-label': 'Main content area, start typing to enter text.',
      },
    },
    onUpdate: ({ editor: ed }) => options.onChange?.(ed.getHTML()),
    onSelectionUpdate: ({ editor: ed }) => syncActive(ed),
    onTransaction: ({ editor: ed }) => syncActive(ed),
  })

  const chain = () => editor.chain().focus()
  /** @type {{ el: HTMLElement, isActive?: (ed: import('@tiptap/core').Editor) => boolean }[]} */
  const tracked = []

  function iconBtn({ icon, title, isActive, onClick, className = '' }) {
    const b = el('button', {
      type: 'button',
      className: `shamar-re-simple__btn${className ? ` ${className}` : ''}`,
      title,
      'aria-label': title,
      html: icon,
    })
    b.addEventListener('click', (e) => {
      e.preventDefault()
      e.stopPropagation()
      onClick(b)
      syncActive(editor)
    })
    tracked.push({ el: b, isActive })
    return b
  }

  function dropdown(triggerIcon, title, items, { isActive } = {}) {
    const wrap = el('div', { className: 'shamar-re-simple__dropdown' })
    const trigger = el('button', {
      type: 'button',
      className: 'shamar-re-simple__btn shamar-re-simple__btn--dropdown',
      title,
      'aria-label': title,
      'aria-haspopup': 'menu',
      'data-state': 'closed',
      html: `${triggerIcon}<span class="shamar-re-simple__chevron">${icons.chevronDown}</span>`,
    })
    const panel = el('div', {
      className: 'shamar-re-simple__popover shamar-re-simple__menu',
      role: 'menu',
      hidden: true,
    })
    items.forEach((item) => {
      const row = el('button', {
        type: 'button',
        className: 'shamar-re-simple__menu-item',
        role: 'menuitem',
        html: `${item.icon || ''}<span>${item.label}</span>`,
      })
      row.addEventListener('click', (e) => {
        e.preventDefault()
        e.stopPropagation()
        item.run()
        panel.hidden = true
        trigger.setAttribute('data-state', 'closed')
        syncActive(editor)
      })
      if (item.isActive) {
        tracked.push({
          el: row,
          isActive: (ed) => {
            const on = !!item.isActive(ed)
            row.classList.toggle('is-active', on)
            return on
          },
        })
      }
      panel.appendChild(row)
    })
    trigger.addEventListener('click', (e) => {
      e.preventDefault()
      e.stopPropagation()
      const open = panel.hidden
      closePopovers(root)
      if (open) {
        placePopover(panel, trigger)
        trigger.setAttribute('data-state', 'open')
      }
    })
    wrap.append(trigger, panel)
    if (isActive) tracked.push({ el: trigger, isActive })
    return wrap
  }

  /* —— Undo / Redo —— */
  const gHistory = group(
    iconBtn({
      icon: icons.undo,
      title: 'Undo',
      onClick: () => chain().undo().run(),
    }),
    iconBtn({
      icon: icons.redo,
      title: 'Redo',
      onClick: () => chain().redo().run(),
    }),
  )

  /* —— Heading dropdown —— */
  const gHeading = dropdown(
    icons.heading,
    'Heading',
    [
      {
        label: 'Paragraph',
        icon: '',
        run: () => chain().setParagraph().run(),
        isActive: (ed) => ed.isActive('paragraph') && !ed.isActive('heading'),
      },
      {
        label: 'Heading 1',
        icon: '',
        run: () => chain().toggleHeading({ level: 1 }).run(),
        isActive: (ed) => ed.isActive('heading', { level: 1 }),
      },
      {
        label: 'Heading 2',
        icon: '',
        run: () => chain().toggleHeading({ level: 2 }).run(),
        isActive: (ed) => ed.isActive('heading', { level: 2 }),
      },
      {
        label: 'Heading 3',
        icon: '',
        run: () => chain().toggleHeading({ level: 3 }).run(),
        isActive: (ed) => ed.isActive('heading', { level: 3 }),
      },
    ],
    { isActive: (ed) => ed.isActive('heading') },
  )

  /* —— List dropdown —— */
  const gList = dropdown(
    icons.list,
    'List',
    [
      {
        label: 'Bullet list',
        icon: icons.list,
        run: () => chain().toggleBulletList().run(),
        isActive: (ed) => ed.isActive('bulletList'),
      },
      {
        label: 'Ordered list',
        icon: icons.listOrdered,
        run: () => chain().toggleOrderedList().run(),
        isActive: (ed) => ed.isActive('orderedList'),
      },
      {
        label: 'Task list',
        icon: icons.listTodo,
        run: () => chain().toggleTaskList().run(),
        isActive: (ed) => ed.isActive('taskList'),
      },
    ],
    {
      isActive: (ed) =>
        ed.isActive('bulletList') || ed.isActive('orderedList') || ed.isActive('taskList'),
    },
  )

  const gBlocks = group(
    iconBtn({
      icon: icons.quote,
      title: 'Blockquote',
      isActive: (ed) => ed.isActive('blockquote'),
      onClick: () => chain().toggleBlockquote().run(),
    }),
    iconBtn({
      icon: icons.code,
      title: 'Code block',
      isActive: (ed) => ed.isActive('codeBlock'),
      onClick: () => chain().toggleCodeBlock().run(),
    }),
  )

  /* —— Marks —— */
  const gMarks = group(
    iconBtn({
      icon: icons.bold,
      title: 'Bold',
      isActive: (ed) => ed.isActive('bold'),
      onClick: () => chain().toggleBold().run(),
    }),
    iconBtn({
      icon: icons.italic,
      title: 'Italic',
      isActive: (ed) => ed.isActive('italic'),
      onClick: () => chain().toggleItalic().run(),
    }),
    iconBtn({
      icon: icons.strike,
      title: 'Strikethrough',
      isActive: (ed) => ed.isActive('strike'),
      onClick: () => chain().toggleStrike().run(),
    }),
    iconBtn({
      icon: icons.code,
      title: 'Code',
      isActive: (ed) => ed.isActive('code'),
      onClick: () => chain().toggleCode().run(),
    }),
    iconBtn({
      icon: icons.underline,
      title: 'Underline',
      isActive: (ed) => ed.isActive('underline'),
      onClick: () => chain().toggleUnderline().run(),
    }),
  )

  /* —— Highlight popover —— */
  const highlightWrap = el('div', { className: 'shamar-re-simple__dropdown' })
  const highlightBtn = iconBtn({
    icon: icons.highlighter,
    title: 'Highlight',
    isActive: (ed) => ed.isActive('highlight'),
    onClick: (b) => {
      const panel = highlightWrap.querySelector('.shamar-re-simple__popover')
      const open = panel.hidden
      closePopovers(root)
      if (open) {
        placePopover(panel, b)
        b.setAttribute('data-state', 'open')
      }
    },
  })
  const highlightPanel = el('div', {
    className: 'shamar-re-simple__popover shamar-re-simple__highlight',
    hidden: true,
  })
  const swatches = el('div', { className: 'shamar-re-simple__swatches' })
  HIGHLIGHT_COLORS.forEach((c) => {
    const sw = el('button', {
      type: 'button',
      className: 'shamar-re-simple__swatch',
      title: c.label,
      'aria-label': c.label,
      style: { background: c.value, borderColor: c.border },
    })
    sw.addEventListener('click', (e) => {
      e.preventDefault()
      e.stopPropagation()
      chain().toggleHighlight({ color: c.value }).run()
      highlightPanel.hidden = true
      highlightBtn.setAttribute('data-state', 'closed')
      syncActive(editor)
    })
    swatches.appendChild(sw)
  })
  const clearHl = el('button', {
    type: 'button',
    className: 'shamar-re-simple__menu-item',
    text: 'Remove highlight',
  })
  clearHl.addEventListener('click', (e) => {
    e.preventDefault()
    chain().unsetHighlight().run()
    highlightPanel.hidden = true
    syncActive(editor)
  })
  highlightPanel.append(swatches, clearHl)
  highlightWrap.append(highlightBtn, highlightPanel)

  /* —— Link popover —— */
  const linkWrap = el('div', { className: 'shamar-re-simple__dropdown' })
  const linkBtn = iconBtn({
    icon: icons.link,
    title: 'Link',
    isActive: (ed) => ed.isActive('link'),
    onClick: (b) => {
      const panel = linkWrap.querySelector('.shamar-re-simple__popover')
      const open = panel.hidden
      closePopovers(root)
      if (open) {
        urlInput.value = editor.getAttributes('link').href || ''
        placePopover(panel, b)
        b.setAttribute('data-state', 'open')
        queueMicrotask(() => urlInput.focus())
      }
    },
  })
  const linkPanel = el('div', {
    className: 'shamar-re-simple__popover shamar-re-simple__link-panel',
    hidden: true,
  })
  const urlInput = el('input', {
    type: 'url',
    className: 'shamar-re-simple__input',
    placeholder: 'Paste a link…',
  })
  const linkApply = el('button', {
    type: 'button',
    className: 'shamar-re-simple__btn-text',
    text: 'Apply',
  })
  const linkRemove = el('button', {
    type: 'button',
    className: 'shamar-re-simple__btn-text',
    text: 'Remove',
  })
  linkApply.addEventListener('click', (e) => {
    e.preventDefault()
    const href = urlInput.value.trim()
    if (!href) chain().extendMarkRange('link').unsetLink().run()
    else chain().extendMarkRange('link').setLink({ href }).run()
    linkPanel.hidden = true
    syncActive(editor)
  })
  linkRemove.addEventListener('click', (e) => {
    e.preventDefault()
    chain().extendMarkRange('link').unsetLink().run()
    linkPanel.hidden = true
    syncActive(editor)
  })
  urlInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      e.preventDefault()
      linkApply.click()
    }
  })
  linkPanel.append(
    urlInput,
    el('div', { className: 'shamar-re-simple__link-actions' }, [linkApply, linkRemove]),
  )
  linkWrap.append(linkBtn, linkPanel)

  const gInline = group(highlightWrap, linkWrap)

  /* —— Align —— */
  const gAlign = group(
    iconBtn({
      icon: icons.alignLeft,
      title: 'Align left',
      isActive: (ed) => ed.isActive({ textAlign: 'left' }),
      onClick: () => chain().setTextAlign('left').run(),
    }),
    iconBtn({
      icon: icons.alignCenter,
      title: 'Align center',
      isActive: (ed) => ed.isActive({ textAlign: 'center' }),
      onClick: () => chain().setTextAlign('center').run(),
    }),
    iconBtn({
      icon: icons.alignRight,
      title: 'Align right',
      isActive: (ed) => ed.isActive({ textAlign: 'right' }),
      onClick: () => chain().setTextAlign('right').run(),
    }),
    iconBtn({
      icon: icons.alignJustify,
      title: 'Justify',
      isActive: (ed) => ed.isActive({ textAlign: 'justify' }),
      onClick: () => chain().setTextAlign('justify').run(),
    }),
  )

  /* —— Image upload —— */
  const fileInput = el('input', {
    type: 'file',
    accept: 'image/*',
    hidden: true,
    style: { display: 'none' },
  })
  fileInput.addEventListener('change', () => {
    const file = fileInput.files?.[0]
    fileInput.value = ''
    if (!file) return
    if (file.size > 5 * 1024 * 1024) {
      window.alert('Image must be 5MB or smaller.')
      return
    }
    const reader = new FileReader()
    reader.onload = () => {
      const src = String(reader.result || '')
      if (src) chain().setImage({ src }).run()
    }
    reader.readAsDataURL(file)
  })
  const gImage = group(
    iconBtn({
      icon: icons.image,
      title: 'Add image',
      onClick: () => fileInput.click(),
    }),
    fileInput,
  )

  /* —— Find & replace panel —— */
  const searchPanel = el('div', {
    className: 'shamar-re-simple__search',
    hidden: true,
  })
  const findInput = el('input', {
    type: 'search',
    className: 'shamar-re-simple__input',
    placeholder: 'Find',
  })
  const replaceInput = el('input', {
    type: 'search',
    className: 'shamar-re-simple__input',
    placeholder: 'Replace',
  })
  const matchCase = el('label', { className: 'shamar-re-simple__check' }, [
    el('input', { type: 'checkbox' }),
    el('span', { text: 'Match case' }),
  ])
  const wholeWord = el('label', { className: 'shamar-re-simple__check' }, [
    el('input', { type: 'checkbox' }),
    el('span', { text: 'Whole words' }),
  ])
  const useRegex = el('label', { className: 'shamar-re-simple__check' }, [
    el('input', { type: 'checkbox' }),
    el('span', { text: 'Regex' }),
  ])
  const searchStatus = el('span', { className: 'shamar-re-simple__search-status', text: '' })
  let searchMatches = []
  let searchIndex = -1

  function buildSearchRegex() {
    const raw = findInput.value
    if (!raw) return null
    const flags = matchCase.querySelector('input').checked ? 'g' : 'gi'
    try {
      if (useRegex.querySelector('input').checked) return new RegExp(raw, flags)
      const escaped = raw.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
      const body = wholeWord.querySelector('input').checked ? `\\b${escaped}\\b` : escaped
      return new RegExp(body, flags)
    } catch {
      return null
    }
  }

  function collectMatches() {
    const re = buildSearchRegex()
    searchMatches = []
    searchIndex = -1
    if (!re) {
      searchStatus.textContent = ''
      return
    }
    const text = editor.state.doc.textContent
    let m
    const local = new RegExp(re.source, re.flags.includes('g') ? re.flags : `${re.flags}g`)
    while ((m = local.exec(text)) !== null) {
      searchMatches.push({ from: m.index, to: m.index + m[0].length, text: m[0] })
      if (m[0].length === 0) local.lastIndex++
    }
    searchStatus.textContent = searchMatches.length
      ? `${searchMatches.length} match${searchMatches.length === 1 ? '' : 'es'}`
      : 'No matches'
  }

  function posAtTextOffset(offset) {
    let remaining = offset
    let found = null
    editor.state.doc.descendants((node, pos) => {
      if (found != null) return false
      if (!node.isText) return true
      const len = node.text.length
      if (remaining <= len) {
        found = pos + remaining
        return false
      }
      remaining -= len
      return true
    })
    return found
  }

  function selectMatch(i) {
    if (!searchMatches.length) return
    searchIndex = ((i % searchMatches.length) + searchMatches.length) % searchMatches.length
    const m = searchMatches[searchIndex]
    const from = posAtTextOffset(m.from)
    const to = posAtTextOffset(m.to)
    if (from == null || to == null) return
    editor.chain().focus().setTextSelection({ from, to }).run()
    searchStatus.textContent = `${searchIndex + 1} of ${searchMatches.length}`
  }

  function replaceCurrent() {
    if (searchIndex < 0 || !searchMatches[searchIndex]) {
      collectMatches()
      if (searchMatches.length) selectMatch(0)
      return
    }
    const replacement = replaceInput.value
    chain().insertContent(replacement).run()
    collectMatches()
    if (searchMatches.length) selectMatch(Math.min(searchIndex, searchMatches.length - 1))
    else searchStatus.textContent = 'No matches'
  }

  function replaceAll() {
    collectMatches()
    if (!searchMatches.length) return
    const ranges = searchMatches
      .map((m) => ({ from: posAtTextOffset(m.from), to: posAtTextOffset(m.to) }))
      .filter((r) => r.from != null && r.to != null)
    for (let i = ranges.length - 1; i >= 0; i--) {
      editor.chain().focus().setTextSelection(ranges[i]).insertContent(replaceInput.value).run()
    }
    collectMatches()
  }

  findInput.addEventListener('input', () => {
    collectMatches()
    if (searchMatches.length) selectMatch(0)
  })
  ;[matchCase, wholeWord, useRegex].forEach((lab) => {
    lab.querySelector('input').addEventListener('change', () => {
      collectMatches()
      if (searchMatches.length) selectMatch(0)
    })
  })

  const btnFindNext = el('button', { type: 'button', className: 'shamar-re-simple__btn-text', text: 'Next' })
  const btnFindPrev = el('button', { type: 'button', className: 'shamar-re-simple__btn-text', text: 'Previous' })
  const btnReplace = el('button', { type: 'button', className: 'shamar-re-simple__btn-text', text: 'Replace' })
  const btnReplaceAll = el('button', {
    type: 'button',
    className: 'shamar-re-simple__btn-text',
    text: 'Replace all',
  })
  const btnCloseSearch = el('button', {
    type: 'button',
    className: 'shamar-re-simple__btn-text',
    text: 'Close',
  })
  btnFindNext.addEventListener('click', (e) => {
    e.preventDefault()
    if (!searchMatches.length) collectMatches()
    selectMatch(searchIndex + 1)
  })
  btnFindPrev.addEventListener('click', (e) => {
    e.preventDefault()
    if (!searchMatches.length) collectMatches()
    selectMatch(searchIndex - 1)
  })
  btnReplace.addEventListener('click', (e) => {
    e.preventDefault()
    replaceCurrent()
  })
  btnReplaceAll.addEventListener('click', (e) => {
    e.preventDefault()
    replaceAll()
  })
  btnCloseSearch.addEventListener('click', (e) => {
    e.preventDefault()
    searchPanel.hidden = true
  })

  searchPanel.append(
    el('div', { className: 'shamar-re-simple__search-row' }, [findInput, replaceInput]),
    el('div', { className: 'shamar-re-simple__search-opts' }, [matchCase, wholeWord, useRegex]),
    el('div', { className: 'shamar-re-simple__search-actions' }, [
      btnFindPrev,
      btnFindNext,
      btnReplace,
      btnReplaceAll,
      btnCloseSearch,
      searchStatus,
    ]),
  )
  surface.appendChild(searchPanel)

  const searchBtn = iconBtn({
    icon: icons.search,
    title: 'Find and replace',
    onClick: () => {
      searchPanel.hidden = !searchPanel.hidden
      if (!searchPanel.hidden) queueMicrotask(() => findInput.focus())
    },
  })

  toolbarScroll.append(
    gHistory,
    sep(),
    group(gHeading, gList),
    gBlocks,
    sep(),
    gMarks,
    gInline,
    sep(),
    gAlign,
    sep(),
    gImage,
  )
  toolbarEnd.append(searchBtn)

  function syncActive(ed) {
    if (!ed) return
    tracked.forEach(({ el: node, isActive }) => {
      if (!isActive) {
        node.classList.remove('is-active')
        node.removeAttribute('data-active-state')
        return
      }
      const on = !!isActive(ed)
      node.classList.toggle('is-active', on)
      node.setAttribute('data-active-state', on ? 'on' : 'off')
    })
  }

  const onDocClick = (e) => {
    if (!root.contains(e.target)) closePopovers(root)
    else if (!e.target.closest('.shamar-re-simple__dropdown')) closePopovers(root)
  }
  const onKeyDown = (e) => {
    if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'f' && root.contains(document.activeElement)) {
      e.preventDefault()
      searchPanel.hidden = false
      findInput.focus()
    }
  }
  document.addEventListener('click', onDocClick)
  document.addEventListener('keydown', onKeyDown)

  syncActive(editor)

  return {
    editor,
    getHTML: () => editor.getHTML(),
    setHTML: (html) => editor.commands.setContent(html || '<p></p>'),
    destroy: () => {
      document.removeEventListener('click', onDocClick)
      document.removeEventListener('keydown', onKeyDown)
      editor.destroy()
      root.innerHTML = ''
    },
  }
}
