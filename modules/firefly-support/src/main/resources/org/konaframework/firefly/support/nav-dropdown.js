/**
 * Top-bar group menus (#505).
 *
 * The bar is one row of groups and ungrouped links. A group (or any item with
 * children) opens a dropdown; nested items open beside their parent. Hover and
 * click both open. Keyboard: the trigger is a button, ArrowDown enters the menu,
 * arrows move, Escape closes.
 *
 * When the row of triggers still overflows, trailing groups move into More as
 * whole menus — children are not laid back out on the bar.
 *
 * The narrow-screen drawer is a separate accordion (nav-accordion.js). This
 * script only touches `.ff-nav-top`.
 */
(function () {
  var SEQ = 0;
  var packing = false;
  var frame = 0;
  var closeTimer = 0;

  function topNavs() {
    return document.querySelectorAll(".ff-nav.ff-nav-top");
  }

  function triggerOf(item) {
    var kids = item.children;
    for (var i = 0; i < kids.length; i++) {
      if (kids[i].classList && kids[i].classList.contains("ff-nav-dropdown-trigger")) return kids[i];
    }
    for (var j = 0; j < kids.length; j++) {
      if (kids[j].tagName === "A") return kids[j];
    }
    return item;
  }

  function menuOf(item) {
    var kids = item.children;
    for (var i = 0; i < kids.length; i++) {
      if (kids[i].tagName === "UL" && kids[i].classList.contains("ff-nav-list")) return kids[i];
    }
    return null;
  }

  function directItem(item) {
    var list = item.parentElement;
    var nav = item.closest(".ff-nav-top");
    if (!nav || !list) return false;
    return list.parentElement === nav;
  }

  function setExpanded(item, open) {
    var button = triggerOf(item);
    if (button && button.tagName === "BUTTON") button.setAttribute("aria-expanded", open ? "true" : "false");
  }

  function closeItem(item) {
    item.classList.remove("ff-open");
    setExpanded(item, false);
    var nested = item.querySelectorAll(".ff-nav-dropdown.ff-open");
    for (var i = 0; i < nested.length; i++) {
      nested[i].classList.remove("ff-open");
      setExpanded(nested[i], false);
    }
  }

  function closeOthers(item) {
    var nav = item.closest(".ff-nav-top");
    if (!nav) return;
    var open = nav.querySelectorAll(".ff-nav-dropdown.ff-open");
    for (var i = 0; i < open.length; i++) {
      if (open[i] === item || open[i].contains(item) || item.contains(open[i])) continue;
      closeItem(open[i]);
    }
  }

  function place(item) {
    var menu = menuOf(item);
    if (!menu || item.closest("[data-ff-nav-more]")) return;
    var anchor = triggerOf(item);
    var rect = anchor.getBoundingClientRect();
    var flyout = !directItem(item);
    menu.style.position = "fixed";
    menu.style.zIndex = "40";
    menu.style.top = Math.round(flyout ? rect.top : rect.bottom + 4) + "px";
    menu.style.left = Math.round(flyout ? rect.right - 4 : rect.left) + "px";
    var box = menu.getBoundingClientRect();
    var pad = 8;
    if (box.right > window.innerWidth - pad) {
      var left = flyout ? rect.left - box.width + 4 : window.innerWidth - box.width - pad;
      menu.style.left = Math.round(Math.max(pad, left)) + "px";
    }
    if (!flyout && box.bottom > window.innerHeight - pad) {
      var above = rect.top - box.height - 4;
      if (above > pad) menu.style.top = Math.round(above) + "px";
    }
  }

  function openItem(item) {
    if (!item || !item.classList.contains("ff-nav-dropdown")) return;
    if (item.closest("[data-ff-nav-more]")) return;
    closeOthers(item);
    item.classList.add("ff-open");
    setExpanded(item, true);
    place(item);
  }

  function remember(node) {
    if (node.getAttribute("data-ff-nav-slot")) return;
    var marker = document.createComment("ff-nav-slot");
    node.parentNode.insertBefore(marker, node);
    node._ffNavMarker = marker;
    node.setAttribute("data-ff-nav-slot", "1");
  }

  function restoreNode(node) {
    var marker = node._ffNavMarker;
    node.removeAttribute("data-ff-nav-slot");
    if (!marker || !marker.parentNode) {
      node._ffNavMarker = null;
      return;
    }
    marker.parentNode.insertBefore(node, marker);
    marker.parentNode.removeChild(marker);
    node._ffNavMarker = null;
  }

  function ensureMore(nav) {
    var more = nav.querySelector("[data-ff-nav-more]");
    if (more) return more;
    SEQ += 1;
    var id = "ff-nav-more-" + SEQ;
    more = document.createElement("div");
    more.className = "ff-menu ff-nav-more";
    more.setAttribute("data-ff-menu", "");
    more.setAttribute("data-ff-nav-more", "");
    more.hidden = true;
    more.innerHTML =
      '<button type="button" class="ff-nav-more-trigger" data-ff-menu-trigger ' +
      'aria-haspopup="menu" aria-expanded="false" aria-controls="' + id + '">More</button>' +
      '<div class="ff-menu-panel" id="' + id + '" data-ff-menu-panel role="menu"></div>';
    nav.appendChild(more);
    return more;
  }

  function restore(nav) {
    var more = nav.querySelector("[data-ff-nav-more]");
    if (!more) return;
    var panel = more.querySelector("[data-ff-menu-panel]");
    if (!panel) return;
    var parked = Array.prototype.slice.call(panel.querySelectorAll("[data-ff-nav-slot]"));
    for (var i = parked.length - 1; i >= 0; i--) restoreNode(parked[i]);
    panel.innerHTML = "";
  }

  function barList(nav) {
    var kids = nav.children;
    for (var i = 0; i < kids.length; i++) {
      if (kids[i].tagName === "UL" && kids[i].classList.contains("ff-nav-list")) return kids[i];
    }
    return null;
  }

  /** Move whole trailing groups into More. Dropdowns stay intact inside each item. */
  function pack(nav) {
    var list = barList(nav);
    if (!list) return;
    restore(nav);
    var more = ensureMore(nav);
    more.hidden = true;
    if (list.clientWidth <= 0) return;
    if (list.scrollWidth <= list.clientWidth + 1) return;
    more.hidden = false;
    var panel = more.querySelector("[data-ff-menu-panel]");
    var bucket = document.createElement("ul");
    bucket.className = "ff-nav-list";
    panel.appendChild(bucket);
    var guard = 0;
    while (list.scrollWidth > list.clientWidth + 1 && guard < 40) {
      guard += 1;
      var items = [];
      for (var i = 0; i < list.children.length; i++) {
        if (list.children[i].tagName === "LI") items.push(list.children[i]);
      }
      if (!items.length) break;
      var last = items[items.length - 1];
      remember(last);
      bucket.insertBefore(last, bucket.firstChild);
    }
    var button = more.querySelector("[data-ff-menu-trigger]");
    if (button) button.classList.toggle("ff-active", !!panel.querySelector(".ff-active"));
  }

  function packAll() {
    if (packing) return;
    packing = true;
    try {
      topNavs().forEach(pack);
    } finally {
      packing = false;
    }
  }

  function schedule() {
    if (packing || frame) return;
    frame = 1;
    window.requestAnimationFrame(function () {
      frame = 0;
      packAll();
    });
  }

  function menuLinks(menu) {
    return Array.prototype.filter.call(menu.querySelectorAll("a[href], button.ff-nav-dropdown-trigger"), function (el) {
      return el.closest(".ff-nav-list") === menu || el.parentElement && el.parentElement.parentElement === menu;
    });
  }

  function onClick(ev) {
    var target = ev.target;
    if (!target || !target.closest) return;
    var button = target.closest(".ff-nav-dropdown-trigger");
    if (!button || !button.closest(".ff-nav-top")) return;
    var item = button.closest(".ff-nav-dropdown");
    if (!item || item.closest("[data-ff-nav-more]")) return;
    ev.preventDefault();
    ev.stopPropagation();
    if (item.classList.contains("ff-open")) closeItem(item);
    else openItem(item);
  }

  function onEnter(ev) {
    var item = ev.target && ev.target.closest ? ev.target.closest(".ff-nav-top .ff-nav-dropdown") : null;
    if (!item || item.closest("[data-ff-nav-more]")) return;
    if (closeTimer) {
      clearTimeout(closeTimer);
      closeTimer = 0;
    }
    openItem(item);
  }

  function onLeave(ev) {
    var item = ev.target && ev.target.closest ? ev.target.closest(".ff-nav-top .ff-nav-dropdown") : null;
    if (!item || !directItem(item)) return;
    var next = ev.relatedTarget;
    if (next && item.contains(next)) return;
    if (closeTimer) clearTimeout(closeTimer);
    closeTimer = setTimeout(function () {
      closeTimer = 0;
      if (!item.contains(document.activeElement)) closeItem(item);
    }, 140);
  }

  function onKey(ev) {
    var target = ev.target;
    if (!target || !target.closest) return;
    var nav = target.closest(".ff-nav-top");
    if (!nav) return;
    var item = target.closest(".ff-nav-dropdown");
    if (ev.key === "Escape") {
      if (!item) return;
      closeItem(item);
      var back = triggerOf(item);
      if (back && back.focus) back.focus();
      return;
    }
    if (!item) return;
    if (ev.key === "ArrowDown" && target.classList && target.classList.contains("ff-nav-dropdown-trigger")) {
      ev.preventDefault();
      openItem(item);
      var menu = menuOf(item);
      var first = menu && menu.querySelector("a[href], button.ff-nav-dropdown-trigger");
      if (first) first.focus();
      return;
    }
    if (ev.key !== "ArrowDown" && ev.key !== "ArrowUp" && ev.key !== "ArrowRight" && ev.key !== "ArrowLeft") return;
    var currentMenu = target.closest(".ff-nav-list");
    if (!currentMenu) return;
    var links = menuLinks(currentMenu);
    var index = links.indexOf(target);
    if (ev.key === "ArrowDown" || ev.key === "ArrowUp") {
      ev.preventDefault();
      var next = ev.key === "ArrowDown" ? links[index + 1] || links[0] : links[index - 1] || links[links.length - 1];
      if (next) next.focus();
    } else if (ev.key === "ArrowRight") {
      var nested = target.closest(".ff-nav-dropdown");
      if (nested && nested !== item) return;
      var child = target.parentElement && target.parentElement.classList.contains("ff-nav-dropdown")
        ? target.parentElement
        : target.closest(".ff-nav-dropdown");
      if (child && menuOf(child)) {
        ev.preventDefault();
        openItem(child);
        var inner = menuOf(child).querySelector("a[href], button.ff-nav-dropdown-trigger");
        if (inner) inner.focus();
      }
    } else if (ev.key === "ArrowLeft" && !directItem(item)) {
      ev.preventDefault();
      closeItem(item);
      var parentTrigger = triggerOf(item);
      if (parentTrigger && parentTrigger.focus) parentTrigger.focus();
    }
  }

  function onDocClick(ev) {
    var target = ev.target;
    if (target && target.closest && target.closest(".ff-nav-top .ff-nav-dropdown")) return;
    document.querySelectorAll(".ff-nav-top .ff-nav-dropdown.ff-open").forEach(closeItem);
  }

  function boot() {
    document.addEventListener("click", onClick);
    document.addEventListener("click", onDocClick);
    document.addEventListener("mouseover", onEnter);
    document.addEventListener("mouseout", onLeave);
    document.addEventListener("keydown", onKey);
    window.addEventListener("resize", schedule);
    document.addEventListener("navigated", schedule);
    document.addEventListener("livewire:navigated", schedule);
    document.addEventListener("kona:navigated", schedule);
    if (document.fonts && document.fonts.ready && document.fonts.ready.then) {
      document.fonts.ready.then(schedule);
    }
    schedule();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", boot);
  } else {
    boot();
  }
})();
