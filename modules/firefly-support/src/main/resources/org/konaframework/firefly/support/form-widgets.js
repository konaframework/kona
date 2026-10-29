(function () {
  if (window.__ffFormWidgets) return;
  window.__ffFormWidgets = true;

  var CDN = 'https://esm.sh/';
  var FILEPOND = '4.32.7';
  var RICH_TITLES = {
    undo: 'Undo', redo: 'Redo', bold: 'Bold', italic: 'Italic', underline: 'Underline', strike: 'Strikethrough',
    code: 'Code', codeBlock: 'Code block', blockquote: 'Blockquote', link: 'Link', highlight: 'Highlight',
    attachFiles: 'Add image', image: 'Add image', alignStart: 'Align left', alignCenter: 'Align center',
    alignEnd: 'Align right', alignJustify: 'Justify', search: 'Find and replace'
  };
  var MARKDOWN_ICONS = {
    heading: 'heading', bold: 'bold', italic: 'italic', strike: 'strike', quote: 'quote', code: 'code', link: 'link',
    image: 'image', list: 'list', ordered: 'listOrdered', task: 'listTodo', table: 'table'
  };

  function $(root, sel) { return root.querySelector(sel); }
  function $$(root, sel) { return Array.prototype.slice.call(root.querySelectorAll(sel)); }
  function parseJson(raw, fallback) { if (!raw) return fallback; try { return JSON.parse(raw); } catch (e) { return fallback; } }
  function token() { var m = document.querySelector('meta[name=csrf-token]'); return m ? m.getAttribute('content') || '' : ''; }
  function once(el, key) { if (el.getAttribute('data-ff-ready-' + key)) return false; el.setAttribute('data-ff-ready-' + key, '1'); return true; }
  function dark() { return !!document.querySelector('.ff-dark') || document.documentElement.classList.contains('ff-dark'); }
  function css(href) {
    if (document.querySelector('link[href="' + href + '"]')) return;
    var link = document.createElement('link'); link.rel = 'stylesheet'; link.href = href; document.head.appendChild(link);
  }

  /* ——— Repeater / Builder serialization ——— */
  var ITEM = '[data-ff-repeater-item],[data-ff-builder-item]';
  var NESTED = '[data-ff-repeater],[data-ff-builder]';
  var JSON_VALUE = '[data-ff-repeater-value],[data-ff-builder-value],[data-ff-key-value-value],[data-ff-checkbox-list-value],[data-ff-toggle-buttons-value]';

  function itemData(container, li) {
    var path = container.getAttribute('data-ff-path') || '';
    var builder = container.hasAttribute('data-ff-builder');
    var prefix = path + '.' + li.getAttribute('data-ff-index') + '.' + (builder ? 'data.' : '');
    var data = {};
    $$(li, '[data-ff-name]').forEach(function (el) {
      if (el.closest(ITEM) !== li) return;
      var name = el.getAttribute('data-ff-name') || '';
      if (name.indexOf(prefix) !== 0) return;
      var key = name.slice(prefix.length);
      if (el.type === 'radio') { if (el.checked) data[key] = el.value; else if (!(key in data)) data[key] = null; return; }
      if (el.type === 'checkbox') { if (el.checked) data[key] = el.value === 'on' || el.value === '1' ? true : el.value; else if (!(key in data)) data[key] = false; return; }
      if (el.matches(JSON_VALUE)) { data[key] = parseJson(el.value, el.value); return; }
      data[key] = el.value;
    });
    return builder ? { type: li.getAttribute('data-ff-type') || '', data: data } : data;
  }

  function serialize(container) {
    var hidden = $(container, container.hasAttribute('data-ff-builder') ? '[data-ff-builder-value]' : '[data-ff-repeater-value]');
    var list = $(container, '[data-ff-repeater-items]');
    if (!hidden || !list) return;
    var items = $$(list, ':scope > ' + ITEM.split(',').join(', :scope > ')).map(function (li) { return itemData(container, li); });
    hidden.value = JSON.stringify(items);
  }

  function touch(node) {
    var c = node && node.closest ? node.closest(NESTED) : null;
    while (c) { serialize(c); c = c.parentElement ? c.parentElement.closest(NESTED) : null; }
  }

  function items(container) {
    var list = $(container, '[data-ff-repeater-items]');
    return list ? $$(list, ':scope > li') : [];
  }

  function refresh(container) {
    var rows = items(container);
    var template = container.getAttribute('data-ff-label-template');
    var numbers = container.getAttribute('data-ff-numbers') === 'true';
    rows.forEach(function (li, i) {
      var label = $(li, ':scope > .ff-repeater-item-head [data-ff-item-label]');
      if (!label) return;
      if (template) label.textContent = template.replace('{n}', String(i + 1));
      else if (numbers) label.textContent = label.textContent.replace(/^\d+\.\s*/, '').replace(/^/, (i + 1) + '. ');
    });
    var max = +(container.getAttribute('data-ff-max') || '0');
    var min = +(container.getAttribute('data-ff-min') || '0');
    var add = $(container, ':scope > .ff-repeater-add, :scope > [data-ff-builder-picker] [data-ff-builder-toggle]');
    if (add) add.disabled = max > 0 && rows.length >= max;
    rows.forEach(function (li) {
      $$(li, ':scope > .ff-repeater-item-head [data-ff-item-delete]').forEach(function (b) { b.disabled = min > 0 && rows.length <= min; });
      $$(li, ':scope > .ff-repeater-item-head [data-ff-item-clone]').forEach(function (b) { b.disabled = max > 0 && rows.length >= max; });
    });
    serialize(container);
    touch(container.parentElement);
  }

  var seq = 0;
  function nextIndex() { seq += 1; return 'n' + Date.now().toString(36) + seq; }

  function insertTemplate(container, template) {
    if (!template) return null;
    var html = template.innerHTML.split('__INDEX__').join(nextIndex());
    var holder = document.createElement('div'); holder.innerHTML = html;
    var li = holder.firstElementChild;
    $(container, '[data-ff-repeater-items]').appendChild(li);
    init(li);
    refresh(container);
    var first = $(li, 'input:not([type=hidden]),textarea,select');
    if (first) first.focus();
    return li;
  }

  function cloneItem(container, li) {
    var old = li.getAttribute('data-ff-index');
    var idx = nextIndex();
    var path = container.getAttribute('data-ff-path') || '';
    var html = li.outerHTML.split(path + '.' + old + '.').join(path + '.' + idx + '.');
    var holder = document.createElement('div'); holder.innerHTML = html;
    var copy = holder.firstElementChild;
    copy.setAttribute('data-ff-index', idx);
    $$(copy, '[data-ff-ready-tags],[data-ff-ready-rich],[data-ff-ready-code],[data-ff-ready-markdown],[data-ff-ready-filepond]').forEach(function (n) {
      Array.prototype.slice.call(n.attributes).forEach(function (a) { if (a.name.indexOf('data-ff-ready-') === 0) n.removeAttribute(a.name); });
    });
    var from = $$(li, 'input,textarea,select');
    var to = $$(copy, 'input,textarea,select');
    from.forEach(function (el, i) { if (!to[i]) return; if (el.type === 'checkbox' || el.type === 'radio') to[i].checked = el.checked; else to[i].value = el.value; });
    li.parentNode.insertBefore(copy, li.nextSibling);
    init(copy);
    refresh(container);
  }

  /* ——— Key-value ——— */
  function syncKeyValue(kv) {
    var out = {};
    $$(kv, '[data-ff-key-value-row]').forEach(function (row) {
      var k = $(row, '[data-ff-key-value-key]'); var v = $(row, '[data-ff-key-value-cell]');
      if (k && k.value.trim() !== '') out[k.value.trim()] = v ? v.value : '';
    });
    var hidden = $(kv, '[data-ff-key-value-value]');
    if (hidden) hidden.value = JSON.stringify(out);
    touch(kv);
  }

  /* ——— Tags ——— */
  function tagValues(box) { return $$(box, '[data-ff-tag]').map(function (c) { return c.getAttribute('data-ff-tag') || ''; }); }
  function syncTags(box) {
    var hidden = $(box, '[data-ff-tags-value]');
    if (hidden) hidden.value = tagValues(box).join(box.getAttribute('data-ff-separator') || ',');
    touch(box);
  }
  function addTag(box, raw) {
    var text = (raw || '').trim();
    if (!text || tagValues(box).indexOf(text) >= 0) return;
    var template = $(box, '[data-ff-tags-template]');
    var holder = document.createElement('div'); holder.innerHTML = template ? template.innerHTML : '';
    var chip = holder.firstElementChild;
    if (!chip) return;
    chip.setAttribute('data-ff-tag', text);
    var label = $(chip, '.ff-tag-label'); if (label) label.textContent = text;
    var remove = $(chip, '[data-ff-tag-remove]'); if (remove) remove.setAttribute('aria-label', 'Remove ' + text);
    var input = $(box, '[data-ff-tags-input]');
    input.parentNode.insertBefore(chip, input);
    syncTags(box);
  }
  function splitKeys(box) {
    var raw = box.getAttribute('data-ff-split') || 'Enter';
    var out = [];
    raw.split(',').forEach(function (k) { var key = k === '' ? ',' : k; if (out.indexOf(key) < 0) out.push(key); });
    return out;
  }

  /* ——— Checkbox list / toggle buttons ——— */
  function syncCheckboxList(list) {
    var vals = $$(list, '[data-ff-checkbox-list-option]:checked').map(function (b) { return b.value; });
    var hidden = $(list, '[data-ff-checkbox-list-value]');
    if (hidden) hidden.value = JSON.stringify(vals);
    var bulk = $(list, '[data-ff-checkbox-list-bulk]');
    if (bulk) {
      var boxes = $$(list, '[data-ff-checkbox-list-option]:not(:disabled)');
      var all = boxes.length > 0 && boxes.every(function (b) { return b.checked; });
      bulk.textContent = all ? bulk.getAttribute('data-ff-deselect-label') : bulk.getAttribute('data-ff-select-label');
    }
    touch(list);
  }
  function filterCheckboxList(list, q) {
    var needle = (q || '').toLowerCase(); var any = false;
    $$(list, '.ff-choice-option').forEach(function (opt) {
      var show = !needle || (opt.getAttribute('data-ff-option-label') || '').indexOf(needle) >= 0;
      opt.hidden = !show; if (show) any = true;
    });
    var empty = $(list, '[data-ff-checkbox-list-empty]'); if (empty) empty.hidden = any;
  }
  function syncToggleButtons(group) {
    $$(group, '[data-ff-toggle-button]').forEach(function (input) {
      var label = input.closest('.ff-toggle-button'); if (label) label.classList.toggle('is-active', input.checked);
    });
    var hidden = $(group, '[data-ff-toggle-buttons-value]');
    if (hidden) hidden.value = JSON.stringify($$(group, '[data-ff-toggle-button]:checked').map(function (b) { return b.value; }));
    touch(group);
  }

  /* ——— Color ——— */
  function hexToRgb(hex) {
    var m = /^#?([0-9a-f]{6})$/i.exec(hex || ''); if (!m) return null;
    var n = parseInt(m[1], 16); return { r: (n >> 16) & 255, g: (n >> 8) & 255, b: n & 255 };
  }
  function toHex(r, g, b) { return '#' + [r, g, b].map(function (x) { var s = Math.max(0, Math.min(255, Math.round(x))).toString(16); return s.length < 2 ? '0' + s : s; }).join(''); }
  function rgbToHsl(c) {
    var r = c.r / 255, g = c.g / 255, b = c.b / 255; var max = Math.max(r, g, b), min = Math.min(r, g, b);
    var h = 0, s = 0, l = (max + min) / 2;
    if (max !== min) {
      var d = max - min; s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
      h = max === r ? (g - b) / d + (g < b ? 6 : 0) : max === g ? (b - r) / d + 2 : (r - g) / d + 4; h /= 6;
    }
    return { h: Math.round(h * 360), s: Math.round(s * 100), l: Math.round(l * 100) };
  }
  function hslToHex(h, s, l) {
    s /= 100; l /= 100;
    var k = function (n) { return (n + h / 30) % 12; };
    var a = s * Math.min(l, 1 - l);
    var f = function (n) { return l - a * Math.max(-1, Math.min(k(n) - 3, Math.min(9 - k(n), 1))); };
    return toHex(f(0) * 255, f(8) * 255, f(4) * 255);
  }
  function formatColor(hex, format) {
    var c = hexToRgb(hex); if (!c) return hex;
    if (format === 'rgb') return 'rgb(' + c.r + ', ' + c.g + ', ' + c.b + ')';
    if (format === 'rgba') return 'rgba(' + c.r + ', ' + c.g + ', ' + c.b + ', 1)';
    if (format === 'hsl') { var h = rgbToHsl(c); return 'hsl(' + h.h + ', ' + h.s + '%, ' + h.l + '%)'; }
    return hex.toLowerCase();
  }
  function colorToHex(text) {
    var t = (text || '').trim();
    var hex = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(t);
    if (hex) { var v = hex[1]; if (v.length === 3) v = v.split('').map(function (x) { return x + x; }).join(''); return '#' + v.toLowerCase(); }
    var rgb = /^rgba?\(\s*(\d+)[\s,]+(\d+)[\s,]+(\d+)/i.exec(t); if (rgb) return toHex(+rgb[1], +rgb[2], +rgb[3]);
    var hsl = /^hsla?\(\s*(\d+)[\s,]+(\d+)%?[\s,]+(\d+)%?/i.exec(t); if (hsl) return hslToHex(+hsl[1], +hsl[2], +hsl[3]);
    return null;
  }

  /* ——— Slider ——— */
  function syncSlider(wrap) {
    var input = $(wrap, '[data-ff-slider-input]'); var out = $(wrap, '[data-ff-slider-value]');
    if (!input) return;
    var min = +(input.min || 0), max = +(input.max || 100), v = +input.value;
    var pct = max > min ? ((v - min) / (max - min)) * 100 : 0;
    input.style.setProperty('--ff-slider-fill', pct + '%');
    if (out) out.textContent = input.value;
  }

  /* ——— Markdown ——— */
  var markdownIt = null;
  function renderMarkdown(src) {
    if (!markdownIt) {
      markdownIt = import(CDN + 'markdown-it@14.1.0').then(function (m) { return (m.default || m)({ html: false, linkify: true, breaks: true }); });
    }
    return markdownIt.then(function (md) { return md.render(src || ''); });
  }
  function markdownMode(box, mode) {
    box.setAttribute('data-ff-mode', mode);
    $$(box, '.ff-markdown-tab').forEach(function (t) { t.setAttribute('aria-selected', t.getAttribute('data-ff-mode') === mode ? 'true' : 'false'); });
    var input = $(box, '[data-ff-write]'); var preview = $(box, '[data-ff-markdown-preview]');
    if (mode === 'preview') {
      input.hidden = true; preview.hidden = false;
      renderMarkdown(input.value).then(function (html) { preview.innerHTML = html || '<p class="ff-hint">Nothing to preview</p>'; }).catch(function () {});
    } else { preview.hidden = true; input.hidden = false; input.focus(); }
  }
  function wrapSelection(input, before, after, placeholder) {
    var s = input.selectionStart, e = input.selectionEnd, v = input.value;
    var picked = v.slice(s, e) || placeholder;
    input.value = v.slice(0, s) + before + picked + after + v.slice(e);
    input.focus(); input.setSelectionRange(s + before.length, s + before.length + picked.length);
    input.dispatchEvent(new Event('input', { bubbles: true }));
  }
  function prefixLines(input, prefix) {
    var s = input.selectionStart, e = input.selectionEnd, v = input.value;
    var start = v.lastIndexOf('\n', s - 1) + 1;
    var block = v.slice(start, e) || '';
    var n = 0;
    var lines = block.split('\n').map(function (line) { n += 1; return (typeof prefix === 'function' ? prefix(n) : prefix) + line; }).join('\n');
    input.value = v.slice(0, start) + lines + v.slice(e);
    input.focus(); input.setSelectionRange(start, start + lines.length);
    input.dispatchEvent(new Event('input', { bubbles: true }));
  }
  function markdownTool(box, tool) {
    var input = $(box, '[data-ff-write]'); if (!input) return;
    if (tool === 'bold') wrapSelection(input, '**', '**', 'bold text');
    else if (tool === 'italic') wrapSelection(input, '_', '_', 'italic text');
    else if (tool === 'strike') wrapSelection(input, '~~', '~~', 'text');
    else if (tool === 'code') wrapSelection(input, '`', '`', 'code');
    else if (tool === 'link') wrapSelection(input, '[', '](https://)', 'link text');
    else if (tool === 'image') wrapSelection(input, '![', '](https://)', 'alt text');
    else if (tool === 'heading') prefixLines(input, '### ');
    else if (tool === 'quote') prefixLines(input, '> ');
    else if (tool === 'list') prefixLines(input, '- ');
    else if (tool === 'ordered') prefixLines(input, function (n) { return n + '. '; });
    else if (tool === 'task') prefixLines(input, '- [ ] ');
    else if (tool === 'table') wrapSelection(input, '\n| Column | Column |\n| --- | --- |\n| ', ' | |\n', 'Cell');
  }

  /* ——— Code editor (Shiki) ——— */
  var shiki = null;
  function highlight(box) {
    var input = $(box, '[data-ff-code-input]'); var out = $(box, '[data-ff-code-highlight]');
    if (!input || !out) return;
    if (!shiki) shiki = import(CDN + 'shiki@1.29.2');
    var lang = box.getAttribute('data-ff-language') || 'text';
    var code = input.value + (input.value.slice(-1) === '\n' ? ' ' : '');
    shiki.then(function (m) {
      return m.codeToHtml(code, { lang: lang, themes: { light: 'github-light', dark: 'github-dark' }, defaultColor: dark() ? 'dark' : 'light' })
        .catch(function () { return m.codeToHtml(code, { lang: 'text', themes: { light: 'github-light', dark: 'github-dark' }, defaultColor: dark() ? 'dark' : 'light' }); });
    }).then(function (html) {
      out.innerHTML = html; box.setAttribute('data-ff-highlighted', 'true');
      out.scrollTop = input.scrollTop; out.scrollLeft = input.scrollLeft;
    }).catch(function () { box.removeAttribute('data-ff-highlighted'); });
  }

  /* ——— Rich editor (Shamar TipTap simple editor) ——— */
  function mountRich(box) {
    var host = $(box, '[data-ff-rich-host]'); var hidden = $(box, '[data-ff-rich-value]');
    if (!host || !hidden) return;
    import('/_firefly/rich-editor/simple.js').then(function (m) {
      var mounted = m.mountSimpleEditor(host, {
        initialHtml: hidden.value,
        editable: !box.hasAttribute('disabled') && box.getAttribute('data-ff-disabled') !== 'true',
        placeholder: box.getAttribute('placeholder') || undefined,
        onChange: function (html) { hidden.value = html; touch(box); }
      });
      if (dark()) host.setAttribute('data-theme', 'dark');
      (box.getAttribute('data-ff-disabled-tools') || '').split(',').forEach(function (tool) {
        var title = RICH_TITLES[tool.trim()]; if (!title) return;
        $$(host, '.shamar-re-simple__toolbar [aria-label="' + title + '"]').forEach(function (b) { b.style.display = 'none'; });
      });
      box.__ffEditor = mounted;
    }).catch(function () { host.classList.add('ff-rich-offline'); });
  }

  /* ——— FilePond ——— */
  var pond = null;
  function filePond() {
    if (pond) return pond;
    css('https://cdn.jsdelivr.net/npm/filepond@' + FILEPOND + '/dist/filepond.min.css');
    css('https://cdn.jsdelivr.net/npm/filepond-plugin-image-preview@4.6.12/dist/filepond-plugin-image-preview.min.css');
    pond = Promise.all([
      import(CDN + 'filepond@' + FILEPOND),
      import(CDN + 'filepond-plugin-image-preview@4.6.12'),
      import(CDN + 'filepond-plugin-file-validate-type@1.2.9'),
      import(CDN + 'filepond-plugin-file-validate-size@2.2.8'),
      import(CDN + 'filepond-plugin-image-exif-orientation@1.0.11'),
      import(CDN + 'filepond-plugin-image-crop@2.0.6'),
      import(CDN + 'filepond-plugin-image-resize@2.0.10'),
      import(CDN + 'filepond-plugin-image-transform@3.8.7')
    ]).then(function (mods) {
      var FilePond = mods[0];
      FilePond.registerPlugin.apply(null, mods.slice(1).map(function (m) { return m.default; }));
      return FilePond;
    });
    return pond;
  }
  function mountFilePond(box) {
    var input = $(box, 'input.filepond'); var hidden = $(box, '[data-ff-upload-value]');
    if (!input || !hidden) return;
    var multiple = box.getAttribute('data-ff-multiple') === 'true';
    var avatar = box.getAttribute('data-ff-avatar') === 'true';
    var accept = (box.getAttribute('data-ff-accept') || '').split(',').filter(Boolean);
    var stored = function () { return multiple ? parseJson(hidden.value, []) : (hidden.value ? [hidden.value] : []); };
    var write = function (paths) { hidden.value = multiple ? JSON.stringify(paths) : (paths[0] || ''); touch(box); };
    filePond().then(function (FilePond) {
      var options = {
        name: 'file',
        allowMultiple: multiple,
        credits: false,
        acceptedFileTypes: accept.length ? accept : null,
        labelIdle: avatar ? 'Drag &amp; Drop your picture or <span class="filepond--label-action">Browse</span>'
          : 'Drag &amp; Drop your files or <span class="filepond--label-action">Browse</span>',
        server: {
          process: {
            url: box.getAttribute('data-ff-upload') || '/_firefly/uploads',
            method: 'POST',
            headers: { 'X-CSRF-TOKEN': token(), 'Accept': 'application/json' },
            withCredentials: true,
            ondata: function (fd) { fd.append('disk', box.getAttribute('data-ff-disk') || 'local'); fd.append('_token', token()); return fd; },
            onload: function (res) { var body = parseJson(res, {}); return body.path || ''; }
          },
          revert: function (path, load) { write(stored().filter(function (p) { return p !== path; })); load(); }
        },
        onprocessfile: function (error, file) {
          if (error) return;
          var path = file.serverId; if (!path) return;
          write(multiple ? stored().concat([path]) : [path]);
        },
        onremovefile: function (error, file) {
          if (file && file.serverId) write(stored().filter(function (p) { return p !== file.serverId; }));
        }
      };
      if (avatar) {
        options.stylePanelLayout = 'compact circle';
        options.imagePreviewHeight = 170;
        options.imageCropAspectRatio = '1:1';
        options.imageResizeTargetWidth = 200;
        options.imageResizeTargetHeight = 200;
        options.styleLoadIndicatorPosition = 'center bottom';
        options.styleProgressIndicatorPosition = 'right bottom';
        options.styleButtonRemoveItemPosition = 'left bottom';
        options.styleButtonProcessItemPosition = 'right bottom';
      } else {
        var crop = box.getAttribute('data-ff-crop'); if (crop) options.imageCropAspectRatio = crop;
        var rw = box.getAttribute('data-ff-resize-width'); if (rw) options.imageResizeTargetWidth = +rw;
        var rh = box.getAttribute('data-ff-resize-height'); if (rh) options.imageResizeTargetHeight = +rh;
        var ph = box.getAttribute('data-ff-preview-height'); if (ph) options.imagePreviewHeight = +ph;
        var layout = box.getAttribute('data-ff-layout'); if (layout) options.stylePanelLayout = layout;
      }
      FilePond.create(input, options);
    }).catch(function () { input.hidden = false; });
  }

  /* ——— Init ——— */
  function init(root) {
    var scope = root && root.querySelectorAll ? root : document;
    var each = function (sel, fn) {
      if (scope.matches && scope.matches(sel)) fn(scope);
      $$(scope, sel).forEach(fn);
    };
    each('[data-ff-slider]', function (w) { if (once(w, 'slider')) syncSlider(w); });
    each('[data-ff-rich-editor]', function (b) { if (!b.closest('template') && once(b, 'rich')) mountRich(b); });
    each('[data-ff-code-editor]', function (b) { if (!b.closest('template') && once(b, 'code')) highlight(b); });
    each('[data-ff-markdown-editor]', function (b) {
      if (b.closest('template') || !once(b, 'markdown')) return;
      import('/_firefly/rich-editor/icons.js').then(function (m) {
        $$(b, '[data-ff-tool]').forEach(function (btn) { var icon = m.icons[MARKDOWN_ICONS[btn.getAttribute('data-ff-tool')]]; if (icon) btn.innerHTML = icon; });
      }).catch(function () {});
    });
    each('.ff-filepond', function (b) { if (!b.closest('template') && once(b, 'filepond')) mountFilePond(b); });
    each('[data-ff-repeater],[data-ff-builder]', function (c) { if (once(c, 'items')) refresh(c); });
  }

  /* ——— Events ——— */
  document.addEventListener('click', function (ev) {
    var t = ev.target; if (!t || !t.closest) return;

    var tab = t.closest('[data-ff-tab-trigger]');
    if (tab) {
      var tabs = tab.closest('[data-ff-tabs]'); var idx = tab.getAttribute('data-ff-tab-trigger');
      $$(tabs, ':scope > .ff-tab-list > [data-ff-tab-trigger]').forEach(function (b) {
        var on = b === tab; b.setAttribute('aria-selected', on ? 'true' : 'false'); if (on) b.setAttribute('aria-current', 'true'); else b.removeAttribute('aria-current');
      });
      $$(tabs, ':scope > [data-ff-tab-pane]').forEach(function (p) { p.hidden = p.getAttribute('data-ff-tab-pane') !== idx; });
      if (tabs.getAttribute('data-ff-tab-query') === 'true') {
        var url = new URL(location.href); url.searchParams.set('tab', String(+idx + 1)); history.replaceState(history.state, '', url);
      }
      return;
    }

    var reveal = t.closest('[data-ff-reveal]');
    if (reveal) {
      var pw = $(reveal.closest('[data-ff-input-group]'), 'input');
      var show = reveal.getAttribute('aria-pressed') !== 'true';
      pw.type = show ? 'text' : 'password';
      reveal.setAttribute('aria-pressed', show ? 'true' : 'false');
      reveal.setAttribute('aria-label', show ? 'Hide password' : 'Show password');
      reveal.title = reveal.getAttribute('aria-label');
      return;
    }

    var copy = t.closest('[data-ff-copy]');
    if (copy) {
      var field = $(copy.closest('[data-ff-input-group]'), 'input');
      var done = function () { copy.classList.add('is-copied'); setTimeout(function () { copy.classList.remove('is-copied'); }, 1500); };
      if (navigator.clipboard && window.isSecureContext) navigator.clipboard.writeText(field.value).then(done, function () {});
      else { var was = field.type; field.type = 'text'; field.select(); try { document.execCommand('copy'); done(); } catch (e) {} field.type = was; field.blur(); }
      return;
    }

    var tagRemove = t.closest('[data-ff-tag-remove]');
    if (tagRemove) { var tbox = tagRemove.closest('[data-ff-tags]'); tagRemove.closest('[data-ff-tag]').remove(); syncTags(tbox); return; }
    var tagsField = t.closest('.ff-tags-field');
    if (tagsField && t === tagsField) { var ti = $(tagsField, '[data-ff-tags-input]'); if (ti) ti.focus(); return; }

    var bulk = t.closest('[data-ff-checkbox-list-bulk]');
    if (bulk) {
      var list = bulk.closest('[data-ff-checkbox-list]');
      var boxes = $$(list, '.ff-choice-option:not([hidden]) [data-ff-checkbox-list-option]:not(:disabled)');
      var all = boxes.every(function (b) { return b.checked; });
      boxes.forEach(function (b) { b.checked = !all; });
      syncCheckboxList(list); return;
    }

    var kv = t.closest('[data-ff-key-value]');
    if (kv) {
      if (t.closest('[data-ff-key-value-add]')) {
        var tpl = $(kv, '[data-ff-key-value-template]'); var holder = document.createElement('div'); holder.innerHTML = tpl ? tpl.innerHTML : '';
        var row = holder.firstElementChild; if (row) { $(kv, '[data-ff-key-value-rows]').appendChild(row); var key = $(row, '[data-ff-key-value-key]'); if (key) key.focus(); }
        syncKeyValue(kv); return;
      }
      var kvRow = t.closest('[data-ff-key-value-row]');
      if (kvRow && t.closest('[data-ff-key-value-remove]')) { kvRow.remove(); syncKeyValue(kv); return; }
      if (kvRow && t.closest('[data-ff-key-value-up]')) { if (kvRow.previousElementSibling) kvRow.parentNode.insertBefore(kvRow, kvRow.previousElementSibling); syncKeyValue(kv); return; }
      if (kvRow && t.closest('[data-ff-key-value-down]')) { if (kvRow.nextElementSibling) kvRow.parentNode.insertBefore(kvRow.nextElementSibling, kvRow); syncKeyValue(kv); return; }
    }

    var container = t.closest(NESTED);
    if (container) {
      var li = t.closest(ITEM);
      if (li && li.closest(NESTED) !== container) li = null;
      if (t.closest('[data-ff-repeater-add]') && t.closest(NESTED) === container) { insertTemplate(container, $(container, ':scope > [data-ff-repeater-template]')); return; }
      var toggle = t.closest('[data-ff-builder-toggle]');
      if (toggle && toggle.closest(NESTED) === container) {
        var menu = $(toggle.closest('[data-ff-builder-picker]'), '[data-ff-builder-menu]'); menu.hidden = !menu.hidden; return;
      }
      var block = t.closest('[data-ff-block]');
      if (block && block.closest(NESTED) === container) {
        block.closest('[data-ff-builder-menu]').hidden = true;
        insertTemplate(container, $(container, ':scope > [data-ff-builder-template="' + block.getAttribute('data-ff-block') + '"]'));
        return;
      }
      if (li) {
        if (t.closest('[data-ff-item-delete]')) { li.remove(); refresh(container); return; }
        if (t.closest('[data-ff-item-clone]')) { cloneItem(container, li); return; }
        if (t.closest('[data-ff-item-up]')) { if (li.previousElementSibling) li.parentNode.insertBefore(li, li.previousElementSibling); refresh(container); return; }
        if (t.closest('[data-ff-item-down]')) { if (li.nextElementSibling) li.parentNode.insertBefore(li.nextElementSibling, li); refresh(container); return; }
        if (t.closest('[data-ff-item-collapse]')) {
          if (li.getAttribute('data-ff-collapsed') === 'true') li.removeAttribute('data-ff-collapsed'); else li.setAttribute('data-ff-collapsed', 'true');
          return;
        }
      }
    }
    $$(document, '[data-ff-builder-menu]:not([hidden])').forEach(function (m) { if (!m.closest('[data-ff-builder-picker]').contains(t)) m.hidden = true; });

    var mdTab = t.closest('.ff-markdown-tab');
    if (mdTab) { markdownMode(mdTab.closest('[data-ff-markdown-editor]'), mdTab.getAttribute('data-ff-mode')); return; }
    var mdTool = t.closest('[data-ff-tool]');
    if (mdTool && mdTool.closest('[data-ff-markdown-editor]')) { markdownTool(mdTool.closest('[data-ff-markdown-editor]'), mdTool.getAttribute('data-ff-tool')); return; }
  });

  document.addEventListener('keydown', function (ev) {
    var t = ev.target; if (!t || !t.matches) return;
    if (t.matches('[data-ff-tags-input]')) {
      var box = t.closest('[data-ff-tags]');
      var keys = splitKeys(box);
      if (ev.key === 'Enter' || keys.indexOf(ev.key) >= 0) { ev.preventDefault(); addTag(box, t.value); t.value = ''; return; }
      if (ev.key === 'Backspace' && t.value === '') { var chips = $$(box, '[data-ff-tag]'); if (chips.length) { chips[chips.length - 1].remove(); syncTags(box); } }
      return;
    }
    if (ev.key === 'Enter' && t.matches('[data-ff-key-value] input, [data-ff-checkbox-list-search]')) { ev.preventDefault(); return; }
    if (ev.key === 'Tab' && !ev.shiftKey && t.matches('[data-ff-code-input]')) {
      ev.preventDefault();
      var s = t.selectionStart, e = t.selectionEnd;
      t.value = t.value.slice(0, s) + '  ' + t.value.slice(e); t.setSelectionRange(s + 2, s + 2);
      t.dispatchEvent(new Event('input', { bubbles: true }));
    }
  });

  document.addEventListener('focusout', function (ev) {
    var t = ev.target;
    if (t && t.matches && t.matches('[data-ff-tags-input]') && t.value.trim() !== '') { addTag(t.closest('[data-ff-tags]'), t.value); t.value = ''; }
  });

  function onValue(ev) {
    var t = ev.target; if (!t || !t.matches) return;
    if (t.matches('[data-ff-slider-input]')) syncSlider(t.closest('[data-ff-slider]'));
    else if (t.matches('[data-ff-color-swatch]')) {
      var color = t.closest('[data-ff-color]'); var text = $(color, '[data-ff-color-value]');
      text.value = formatColor(t.value, color.getAttribute('data-ff-format') || 'hex');
    } else if (t.matches('[data-ff-color-value]')) {
      var hex = colorToHex(t.value); var swatch = $(t.closest('[data-ff-color]'), '[data-ff-color-swatch]'); if (hex && swatch) swatch.value = hex;
    } else if (t.matches('[data-ff-checkbox-list-option]')) syncCheckboxList(t.closest('[data-ff-checkbox-list]'));
    else if (t.matches('[data-ff-checkbox-list-search]')) filterCheckboxList(t.closest('[data-ff-checkbox-list]'), t.value);
    else if (t.matches('[data-ff-toggle-button]')) {
      if (t.type === 'radio' && t.hasAttribute('data-ff-name')) {
        $$(t.closest('[data-ff-toggle-buttons]'), '[data-ff-toggle-button]').forEach(function (b) { if (b !== t) b.checked = false; });
      }
      syncToggleButtons(t.closest('[data-ff-toggle-buttons]'));
    } else if (t.matches('[data-ff-key-value-key],[data-ff-key-value-cell]')) syncKeyValue(t.closest('[data-ff-key-value]'));
    else if (t.matches('[data-ff-code-input]')) highlight(t.closest('[data-ff-code-editor]'));
    if (t.type === 'radio' && t.hasAttribute('data-ff-name') && t.checked) {
      var item = t.closest(ITEM);
      if (item) $$(item, 'input[type=radio][data-ff-name="' + t.getAttribute('data-ff-name') + '"]').forEach(function (r) { if (r !== t) r.checked = false; });
    }
    if (t.closest(ITEM)) touch(t);
  }
  document.addEventListener('input', onValue);
  document.addEventListener('change', onValue);

  document.addEventListener('scroll', function (ev) {
    var t = ev.target;
    if (t && t.matches && t.matches('[data-ff-code-input]')) {
      var out = $(t.closest('[data-ff-code-editor]'), '[data-ff-code-highlight]'); if (out) { out.scrollTop = t.scrollTop; out.scrollLeft = t.scrollLeft; }
    }
  }, true);

  document.addEventListener('submit', function (ev) {
    var form = ev.target; if (!form || !form.querySelectorAll) return;
    $$(form, NESTED).reverse().forEach(serialize);
  }, true);

  function boot() {
    init(document);
    if (!window.MutationObserver) return;
    var pending = false;
    new MutationObserver(function () {
      if (pending) return; pending = true;
      requestAnimationFrame(function () { pending = false; init(document); });
    }).observe(document.body, { childList: true, subtree: true });
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot); else boot();
})();
