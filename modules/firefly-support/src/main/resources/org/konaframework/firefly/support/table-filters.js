(function () {
  var root = document.querySelector('.ff-panel');
  if (!root || root.getAttribute('data-ff-filters-ready') === 'true') return;
  root.setAttribute('data-ff-filters-ready', 'true');

  var pending = null;
  var swappedAt = 0;
  var liveTimer = null;
  var COLLAPSE_KEY = 'ff-filters-open:';

  function wraps() { return Array.prototype.slice.call(document.querySelectorAll('.ff-table-wrap')); }
  function wrapIndex(wrap) { return wraps().indexOf(wrap); }
  function toolbarOf(el) { var wrap = el.closest('.ff-table-wrap'); return wrap ? wrap.querySelector('.ff-table-toolbar') : null; }
  function panelFor(trigger) { var bar = toolbarOf(trigger); return bar ? bar.querySelector('[data-ff-filters-panel]') : null; }
  function triggerIn(wrap) { return wrap ? wrap.querySelector('[data-ff-filters-trigger]') : null; }

  function positionDropdown(panel, trigger) {
    if (!panel || !trigger) return;
    var rect = trigger.getBoundingClientRect();
    var pad = 12;
    var gap = 8;
    panel.style.position = 'fixed';
    var width = Math.min(panel.offsetWidth || 320, window.innerWidth - pad * 2);
    var left = rect.right - width;
    left = Math.min(Math.max(pad, left), Math.max(pad, window.innerWidth - width - pad));
    var top = rect.bottom + gap;
    var room = window.innerHeight - top - pad;
    var height = panel.scrollHeight;
    if (height > room && rect.top - gap - height >= pad) top = rect.top - gap - height;
    panel.style.left = Math.round(left) + 'px';
    panel.style.top = Math.round(top) + 'px';
    panel.style.maxHeight = Math.max(160, window.innerHeight - top - pad) + 'px';
  }

  function openDropdown(panel, trigger) {
    closeDropdowns(panel);
    panel.removeAttribute('hidden');
    panel.classList.add('ff-open');
    trigger.setAttribute('aria-expanded', 'true');
    positionDropdown(panel, trigger);
  }

  function closeDropdown(panel) {
    if (!panel || panel.hasAttribute('hidden')) return;
    panel.setAttribute('hidden', '');
    panel.classList.remove('ff-open');
    var trigger = triggerIn(panel.closest('.ff-table-wrap'));
    if (trigger) trigger.setAttribute('aria-expanded', 'false');
  }

  function closeDropdowns(except) {
    document.querySelectorAll('[data-ff-filters-layout="dropdown"].ff-open').forEach(function (p) {
      if (p !== except) closeDropdown(p);
    });
  }

  function openModal(panel) {
    panel.classList.add('ff-open');
    panel.removeAttribute('hidden');
    panel.style.display = 'block';
    panel.removeAttribute('aria-hidden');
  }

  function collapseKey(wrap) { return COLLAPSE_KEY + location.pathname + ':' + wrapIndex(wrap); }

  function setCollapsible(panel, trigger, open) {
    if (open) panel.removeAttribute('hidden'); else panel.setAttribute('hidden', '');
    trigger.setAttribute('aria-expanded', open ? 'true' : 'false');
    try { sessionStorage.setItem(collapseKey(panel.closest('.ff-table-wrap')), open ? '1' : '0'); } catch (e) {}
  }

  function open(trigger) {
    var panel = panelFor(trigger);
    if (!panel) return null;
    var layout = panel.getAttribute('data-ff-filters-layout');
    if (layout === 'dropdown') openDropdown(panel, trigger);
    else if (layout === 'modal') openModal(panel);
    else if (layout === 'collapsible') setCollapsible(panel, trigger, true);
    return panel;
  }

  function restoreCollapsibles() {
    wraps().forEach(function (wrap) {
      var panel = wrap.querySelector('[data-ff-filters-layout="collapsible"]');
      var trigger = triggerIn(wrap);
      if (!panel || !trigger) return;
      var stored = null;
      try { stored = sessionStorage.getItem(collapseKey(wrap)); } catch (e) {}
      if (stored === '1') { panel.removeAttribute('hidden'); trigger.setAttribute('aria-expanded', 'true'); }
    });
  }

  function submit(form) {
    if (!form) return;
    if (form.requestSubmit) form.requestSubmit(); else form.dispatchEvent(new Event('submit', { cancelable: true, bubbles: true }));
  }

  function scheduleLive(form, delay) {
    if (!form || form.getAttribute('data-ff-filters-live') !== 'true') return;
    clearTimeout(liveTimer);
    liveTimer = setTimeout(function () { submit(form); }, delay);
  }

  // ---- query builder ----

  function enc(v) { return encodeURIComponent(v); }

  function ruleValues(rule) {
    var wrap = rule.querySelector('[data-ff-qb-value]');
    if (!wrap || wrap.hasAttribute('hidden')) return [];
    var input = wrap.querySelector('[data-ff-qb-input]');
    if (!input) return [];
    if (input.multiple) {
      return Array.prototype.filter.call(input.options, function (o) { return o.selected && o.value; }).map(function (o) { return o.value; });
    }
    return input.value ? [input.value] : [];
  }

  function serializeBuilder(qb) {
    var groups = [];
    qb.querySelectorAll('[data-ff-qb-group]').forEach(function (group) {
      var rules = [];
      group.querySelectorAll('[data-ff-qb-rule]').forEach(function (rule) {
        var op = rule.querySelector('[data-ff-qb-operator]');
        var choice = op && op.options[op.selectedIndex];
        if (!choice) return;
        var needs = choice.getAttribute('data-ff-qb-novalue') !== 'true';
        var values = ruleValues(rule);
        if (needs && !values.length) return;
        rules.push(enc(rule.getAttribute('data-ff-qb-constraint')) + ':' + op.value + ':' + values.map(enc).join(','));
      });
      if (rules.length) groups.push(rules.join(';'));
    });
    var state = qb.querySelector('[data-ff-qb-state]');
    var next = groups.join('|');
    if (state && state.value !== next) {
      state.value = next;
      scheduleLive(qb.closest('form'), 350);
    }
  }

  function syncOperator(rule) {
    var op = rule.querySelector('[data-ff-qb-operator]');
    var wrap = rule.querySelector('[data-ff-qb-value]');
    if (!op || !wrap) return;
    var choice = op.options[op.selectedIndex];
    var none = !choice || choice.getAttribute('data-ff-qb-novalue') === 'true' || !wrap.querySelector('[data-ff-qb-input]');
    if (none) wrap.setAttribute('hidden', ''); else wrap.removeAttribute('hidden');
  }

  function cloneTemplate(qb, selector) {
    var tpl = qb.querySelector(selector);
    if (!tpl) return null;
    var holder = document.createElement('div');
    holder.innerHTML = tpl.innerHTML;
    return holder.firstElementChild;
  }

  // ---- events ----

  document.addEventListener('click', function (ev) {
    var t = ev.target;
    if (!t || !t.closest) return;

    var trigger = t.closest('[data-ff-filters-trigger]');
    if (trigger) {
      ev.preventDefault();
      var panel = panelFor(trigger);
      if (!panel) return;
      var layout = panel.getAttribute('data-ff-filters-layout');
      if (layout === 'dropdown' && !panel.hasAttribute('hidden')) { closeDropdown(panel); return; }
      if (layout === 'collapsible' && !panel.hasAttribute('hidden')) { setCollapsible(panel, trigger, false); return; }
      // Chrome's catch-all click closes open dialogs; open after it runs.
      setTimeout(function () { open(trigger); }, 0);
      return;
    }

    var reset = t.closest('[data-ff-filters-reset]');
    if (reset) {
      var form = reset.closest('form[data-ff-filters-form]');
      if (!form) return;
      ev.preventDefault();
      var sentinel = form.querySelector('[data-ff-filters-sentinel]');
      if (sentinel) sentinel.value = 'reset';
      submit(form);
      return;
    }

    var addGroup = t.closest('[data-ff-qb-add-group]');
    if (addGroup) {
      var qbA = addGroup.closest('[data-ff-qb]');
      var group = cloneTemplate(qbA, 'template[data-ff-qb-group-template]');
      if (group) qbA.querySelector('[data-ff-qb-groups]').appendChild(group);
      return;
    }

    var removeGroup = t.closest('[data-ff-qb-remove-group]');
    if (removeGroup) {
      var qbG = removeGroup.closest('[data-ff-qb]');
      var groups = qbG.querySelectorAll('[data-ff-qb-group]');
      var mine = removeGroup.closest('[data-ff-qb-group]');
      if (groups.length > 1) mine.remove();
      else mine.querySelector('[data-ff-qb-rules]').innerHTML = '';
      serializeBuilder(qbG);
      return;
    }

    var removeRule = t.closest('[data-ff-qb-remove]');
    if (removeRule) {
      var qbR = removeRule.closest('[data-ff-qb]');
      removeRule.closest('[data-ff-qb-rule]').remove();
      serializeBuilder(qbR);
      return;
    }

    if (!t.closest('[data-ff-filters-layout="dropdown"]') && !t.closest('[data-ff-combobox-panel],[data-ff-picker-panel]')) closeDropdowns(null);
  });

  document.addEventListener('change', function (ev) {
    var el = ev.target;
    if (!el || !el.closest) return;
    var qb = el.closest('[data-ff-qb]');
    if (qb) {
      if (el.matches('[data-ff-qb-add]')) {
        var name = el.value;
        el.value = '';
        if (!name) return;
        var rule = cloneTemplate(qb, 'template[data-ff-qb-template="' + name.replace(/"/g, '\\"') + '"]');
        if (!rule) return;
        el.closest('[data-ff-qb-group]').querySelector('[data-ff-qb-rules]').appendChild(rule);
        syncOperator(rule);
        var focus = rule.querySelector('[data-ff-qb-input]');
        if (focus && focus.focus) focus.focus();
        serializeBuilder(qb);
        return;
      }
      if (el.matches('[data-ff-qb-operator]')) syncOperator(el.closest('[data-ff-qb-rule]'));
      serializeBuilder(qb);
      return;
    }
    var form = el.closest('form[data-ff-filters-live="true"]');
    if (form) scheduleLive(form, 0);
  });

  document.addEventListener('input', function (ev) {
    var el = ev.target;
    if (!el || !el.closest) return;
    var qb = el.closest('[data-ff-qb]');
    if (qb) { serializeBuilder(qb); return; }
    if (el.matches('input[type="text"],input[type="search"],input[type="number"],input:not([type]),textarea')) {
      scheduleLive(el.closest('form[data-ff-filters-live="true"]'), 450);
    }
  });

  // Remember which panel was open so live updates reopen it after the soft-nav swap.
  document.addEventListener('submit', function (ev) {
    var form = ev.target;
    if (!form || !form.matches || !form.matches('form[data-ff-filters-form]')) return;
    form.querySelectorAll('[data-ff-qb]').forEach(serializeBuilder);
    // The server reads one value per key: send native multi-selects comma-joined.
    form.querySelectorAll('select[multiple][data-ff-filter-input]').forEach(function (select) {
      var name = select.getAttribute('name') || select.getAttribute('data-ff-multi-name');
      if (!name) return;
      select.setAttribute('data-ff-multi-name', name);
      select.removeAttribute('name');
      var proxy = select.parentNode.querySelector('input[data-ff-multi-proxy]');
      if (!proxy) {
        proxy = document.createElement('input');
        proxy.type = 'hidden';
        proxy.setAttribute('data-ff-multi-proxy', '');
        select.parentNode.appendChild(proxy);
      }
      proxy.name = name;
      proxy.value = Array.prototype.filter.call(select.options, function (o) { return o.selected && o.value; }).map(function (o) { return o.value; }).join(',');
    });
    // With {prefix}=1 an absent field already means "not set"; keep blank fields out of the URL.
    var prefixRoot = form.getAttribute('data-ff-filters-prefix');
    if (!prefixRoot) {
      var scoped = form.closest('[data-ff-filters-prefix]');
      prefixRoot = scoped ? scoped.getAttribute('data-ff-filters-prefix') : null;
    }
    var prefix = (prefixRoot || 'filters') + '.';
    var blank = Array.prototype.filter.call(form.elements, function (el) {
      return el.name && el.name.indexOf(prefix) === 0 && !el.disabled && el.value === '' && el.type !== 'checkbox' && (el.type !== 'radio' || el.checked);
    });
    blank.forEach(function (el) { el.disabled = true; });
    setTimeout(function () { blank.forEach(function (el) { el.disabled = false; }); }, 0);
    var panel = form.closest('[data-ff-filters-panel]');
    var wrap = form.closest('.ff-table-wrap');
    var active = document.activeElement;
    var live = form.getAttribute('data-ff-filters-live') === 'true';
    var reset = (form.querySelector('[data-ff-filters-sentinel]') || {}).value === 'reset';
    pending = {
      index: wrapIndex(wrap),
      reopen: !!(panel && live && !reset && panel.getAttribute('data-ff-filters-layout') !== 'above' && panel.getAttribute('data-ff-filters-layout') !== 'below'),
      focus: active && form.contains(active) ? (active.getAttribute('name') || active.getAttribute('data-ff-filter-focus') || null) : null,
    };
    if (wrap) wrap.dispatchEvent(new CustomEvent('ff:filters-changed', { bubbles: true }));
    if (!live && panel && panel.getAttribute('data-ff-filters-layout') === 'dropdown') closeDropdown(panel);
  }, true);

  function afterSwap() {
    swappedAt = Date.now();
    document.querySelectorAll('[data-ff-qb-rule]').forEach(syncOperator);
    restoreCollapsibles();
    if (!pending) return;
    var wrap = wraps()[pending.index];
    if (!wrap) return;
    var state = pending;
    pending = null;
    if (state.reopen) {
      var trigger = triggerIn(wrap);
      var panel = trigger ? open(trigger) : null;
      if (panel && state.focus) {
        var field = panel.querySelector('[name="' + state.focus.replace(/"/g, '\\"') + '"]');
        if (field && field.focus) {
          field.focus();
          try { var end = field.value.length; field.setSelectionRange(end, end); } catch (e) {}
        }
      }
    }
  }

  new MutationObserver(function (records) {
    var swapped = false;
    records.forEach(function (r) {
      if (r.type === 'childList') {
        r.addedNodes.forEach(function (n) { if (n.nodeType === 1 && (n.matches('.ff-table-wrap') || n.querySelector('.ff-table-wrap'))) swapped = true; });
        return;
      }
      if (r.oldValue === r.target.getAttribute(r.attributeName) || Date.now() - swappedAt < 300) return;
      var form = r.target.closest && r.target.closest('form[data-ff-filters-live="true"]');
      if (form && !r.target.closest('[data-ff-qb]')) scheduleLive(form, 0);
    });
    if (swapped) afterSwap();
  }).observe(root, { childList: true, subtree: true, attributes: true, attributeOldValue: true, attributeFilter: ['data-ff-state', 'aria-pressed'] });

  document.addEventListener('keydown', function (ev) { if (ev.key === 'Escape') closeDropdowns(null); });

  function reposition() {
    document.querySelectorAll('[data-ff-filters-layout="dropdown"].ff-open').forEach(function (panel) {
      positionDropdown(panel, triggerIn(panel.closest('.ff-table-wrap')));
    });
  }
  window.addEventListener('resize', reposition);
  window.addEventListener('scroll', reposition, true);

  document.querySelectorAll('[data-ff-qb-rule]').forEach(syncOperator);
  restoreCollapsibles();
})();
