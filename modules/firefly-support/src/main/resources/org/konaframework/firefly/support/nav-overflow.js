/**
 * Top-bar overflow (#493).
 *
 * APP, INVERSE, and TOP draw links in `.ff-nav-top`. When that row is wider than
 * the bar, trailing items move into a More menu. A cluster or parent that only
 * partly fits keeps its label in the bar; the children that do not fit stay
 * grouped under that label. A node that does not fit at all moves with its
 * descendants, so level 2 and level 3 structure is kept.
 *
 * SIDE nav is not measured (selector is `.ff-nav-top` only).
 * Re-measures on resize and after soft navigations that replace the bar.
 */
(function () {
  var SEQ = 0;
  var packing = false;
  var frame = 0;
  var obs = null;

  function navs() {
    return document.querySelectorAll(".ff-nav.ff-nav-top");
  }

  function lis(ul) {
    var out = [];
    if (!ul) return out;
    for (var i = 0; i < ul.children.length; i++) {
      if (ul.children[i].tagName === "LI") out.push(ul.children[i]);
    }
    return out;
  }

  function sublist(li) {
    for (var i = 0; i < li.children.length; i++) {
      var el = li.children[i];
      if (el.tagName === "UL" && el.classList.contains("ff-nav-list")) return el;
    }
    return null;
  }

  function linkOf(li) {
    for (var i = 0; i < li.children.length; i++) {
      if (li.children[i].tagName === "A") return li.children[i];
    }
    return null;
  }

  function labelText(li) {
    var label = li.querySelector(":scope > a .ff-nav-label");
    var text = label ? label.textContent : "";
    if (!text) {
      var link = linkOf(li);
      text = link ? link.textContent : "";
    }
    return (text || "").replace(/\s+/g, " ").trim();
  }

  function gapPx(el) {
    if (!el) return 0;
    var styles = window.getComputedStyle(el);
    var raw = (styles && (styles.columnGap || styles.gap)) || "0";
    if (!raw || raw === "normal") return 0;
    var n = parseFloat(raw);
    return isNaN(n) ? 0 : n;
  }

  function box(el) {
    if (!el) return 0;
    var rect = el.getBoundingClientRect();
    var w = rect && rect.width > 0 ? rect.width : el.offsetWidth || 0;
    return Math.ceil(w);
  }

  function barList(nav) {
    for (var i = 0; i < nav.children.length; i++) {
      var el = nav.children[i];
      if (el.tagName === "UL" && el.classList.contains("ff-nav-list")) return el;
    }
    return null;
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
    node.querySelectorAll("[data-ff-nav-menuitem]").forEach(function (anchor) {
      anchor.removeAttribute("role");
      anchor.removeAttribute("data-ff-nav-menuitem");
    });
    if (!marker || !marker.parentNode) {
      node._ffNavMarker = null;
      return;
    }
    marker.parentNode.insertBefore(node, marker);
    marker.parentNode.removeChild(marker);
    node._ffNavMarker = null;
  }

  function restore(nav) {
    var more = nav.querySelector("[data-ff-nav-more]");
    if (!more) return;
    var panel = more.querySelector("[data-ff-menu-panel]");
    if (!panel) return;
    var parked = Array.prototype.slice.call(panel.querySelectorAll("[data-ff-nav-slot]"));
    for (var i = parked.length - 1; i >= 0; i--) restoreNode(parked[i]);
    panel.querySelectorAll(".ff-nav-more-group").forEach(function (group) {
      group.remove();
    });
    panel.querySelectorAll(":scope > .ff-nav-list").forEach(function (ul) {
      ul.remove();
    });
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

  function markItems(root) {
    root.querySelectorAll("a[href]").forEach(function (anchor) {
      if (anchor.getAttribute("data-ff-nav-menuitem") === "1") return;
      anchor.setAttribute("role", "menuitem");
      anchor.setAttribute("data-ff-nav-menuitem", "1");
    });
  }

  function placeAll(panel, moves) {
    var index = 0;
    while (index < moves.length) {
      var heading = moves[index].heading;
      var list = document.createElement("ul");
      list.className = "ff-nav-list";
      if (heading) {
        var group = document.createElement("div");
        group.className = "ff-nav-more-group";
        group.setAttribute("role", "group");
        group.setAttribute("data-ff-nav-heading", heading);
        group.setAttribute("aria-label", heading);
        var title = document.createElement("div");
        title.className = "ff-nav-more-label";
        title.textContent = heading;
        group.appendChild(title);
        group.appendChild(list);
        panel.appendChild(group);
        while (index < moves.length && moves[index].heading === heading) {
          remember(moves[index].node);
          markItems(moves[index].node);
          list.appendChild(moves[index].node);
          index += 1;
        }
      } else {
        panel.appendChild(list);
        while (index < moves.length && !moves[index].heading) {
          remember(moves[index].node);
          markItems(moves[index].node);
          list.appendChild(moves[index].node);
          index += 1;
        }
      }
    }
  }

  /**
   * What of `li` fits in `budget` pixels. Does not move nodes.
   * `used` is the width still occupied in the parent row (0 when the whole node moves).
   * `moves` lists overflow nodes in visual order, with `heading` set when the
   * parent label stays in the bar.
   */
  function planItem(li, budget) {
    var sub = sublist(li);
    var kids = sub ? lis(sub) : [];
    if (!kids.length) {
      var width = box(li);
      if (width <= budget) return { keep: true, used: width, moves: [] };
      return { keep: false, used: 0, moves: [{ node: li, heading: null }] };
    }
    var link = linkOf(li);
    var labelW = box(link);
    var between = gapPx(li);
    var href = link && link.getAttribute("href");
    var heading = labelText(li);
    if (labelW > budget) {
      return { keep: false, used: 0, moves: [{ node: li, heading: null }] };
    }
    var chrome = labelW + between;
    if (chrome > budget) {
      if (!href) return { keep: false, used: 0, moves: [{ node: li, heading: null }] };
      return {
        keep: true,
        used: labelW,
        moves: kids.map(function (kid) { return { node: kid, heading: heading }; }),
      };
    }
    var remain = budget - chrome;
    var subGap = gapPx(sub);
    var used = chrome;
    var moves = [];
    var kept = false;
    var childOverflow = false;
    for (var i = 0; i < kids.length; i++) {
      if (childOverflow) {
        moves.push({ node: kids[i], heading: heading });
        continue;
      }
      var gapAdd = kept ? subGap : 0;
      var room = remain - gapAdd;
      if (room < 0) room = 0;
      var planned = planItem(kids[i], room);
      if (planned.keep) {
        var take = planned.used + gapAdd;
        if (take <= remain) {
          remain -= take;
          used += take;
          kept = true;
          if (planned.moves.length) moves = moves.concat(planned.moves);
          continue;
        }
      }
      childOverflow = true;
      moves.push({ node: kids[i], heading: heading });
    }
    if (!kept && !href) {
      return { keep: false, used: 0, moves: [{ node: li, heading: null }] };
    }
    return { keep: true, used: used, moves: moves };
  }

  function planRow(items, budget, gap) {
    var remain = budget;
    var moves = [];
    var kept = 0;
    var overflow = false;
    for (var i = 0; i < items.length; i++) {
      if (overflow) {
        moves.push({ node: items[i], heading: null });
        continue;
      }
      var gapAdd = kept > 0 ? gap : 0;
      var room = remain - gapAdd;
      if (room < 0) room = 0;
      var planned = planItem(items[i], room);
      if (!planned.keep) {
        overflow = true;
        moves.push({ node: items[i], heading: null });
        continue;
      }
      var take = planned.used + gapAdd;
      if (take > remain) {
        overflow = true;
        moves.push({ node: items[i], heading: null });
        continue;
      }
      remain -= take;
      kept += 1;
      if (planned.moves.length) {
        overflow = true;
        moves = moves.concat(planned.moves);
      }
    }
    return moves;
  }

  function sync(more) {
    var button = more.querySelector("[data-ff-menu-trigger]");
    if (!button) return;
    var open = !more.hidden && more.classList.contains("ff-open");
    button.setAttribute("aria-expanded", open ? "true" : "false");
  }

  function pack(nav) {
    var list = barList(nav);
    if (!list) return;
    var wasOpen = false;
    var existing = nav.querySelector("[data-ff-nav-more]");
    if (existing) wasOpen = existing.classList.contains("ff-open");
    restore(nav);
    var more = ensureMore(nav);
    more.hidden = true;
    more.classList.remove("ff-open");
    if (list.clientWidth <= 0) return;
    if (list.scrollWidth <= list.clientWidth + 1) {
      sync(more);
      return;
    }
    more.hidden = false;
    var budget = Math.max(0, list.clientWidth - 4);
    var panel = more.querySelector("[data-ff-menu-panel]");
    placeAll(panel, planRow(lis(list), budget, gapPx(list)));
    var guard = 0;
    while (list.scrollWidth > list.clientWidth + 1 && guard < 40) {
      guard += 1;
      var items = lis(list);
      if (!items.length) break;
      placeAll(panel, [{ node: items[items.length - 1], heading: null }]);
    }
    if (!panel.querySelector("[data-ff-nav-slot]")) {
      more.hidden = true;
      more.classList.remove("ff-open");
    } else if (wasOpen) {
      more.classList.add("ff-open");
    }
    var trigger = more.querySelector("[data-ff-menu-trigger]");
    if (trigger) trigger.classList.toggle("ff-active", !!panel.querySelector(".ff-active"));
    sync(more);
  }

  function packAll() {
    if (packing) return;
    packing = true;
    if (obs) obs.disconnect();
    try {
      navs().forEach(pack);
    } finally {
      packing = false;
      if (obs) {
        obs.observe(document.documentElement, { childList: true, subtree: true });
      }
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

  function menuItems(panel) {
    return Array.prototype.filter.call(panel.querySelectorAll('[role="menuitem"]'), function (el) {
      return !!el.offsetParent;
    });
  }

  function onKey(ev) {
    var target = ev.target;
    if (!target || !target.closest) return;
    var more = target.closest("[data-ff-nav-more]");
    if (!more || more.hidden) return;
    var button = more.querySelector("[data-ff-menu-trigger]");
    var panel = more.querySelector("[data-ff-menu-panel]");
    if (!button || !panel) return;
    if (ev.key === "ArrowDown" && target === button) {
      ev.preventDefault();
      more.classList.add("ff-open");
      sync(more);
      var first = menuItems(panel)[0];
      if (first) first.focus();
      return;
    }
    if (ev.key === "Escape") {
      if (!more.classList.contains("ff-open")) return;
      more.classList.remove("ff-open");
      sync(more);
      button.focus();
      return;
    }
    if (!more.classList.contains("ff-open")) return;
    var entries = menuItems(panel);
    var index = entries.indexOf(target);
    if (ev.key === "ArrowDown") {
      ev.preventDefault();
      var next = entries[index + 1] || entries[0];
      if (next) next.focus();
    } else if (ev.key === "ArrowUp") {
      ev.preventDefault();
      if (index <= 0) button.focus();
      else entries[index - 1].focus();
    } else if (ev.key === "Home") {
      ev.preventDefault();
      if (entries[0]) entries[0].focus();
    } else if (ev.key === "End") {
      ev.preventDefault();
      if (entries.length) entries[entries.length - 1].focus();
    }
  }

  function boot() {
    obs = new MutationObserver(schedule);
    window.addEventListener("resize", schedule);
    document.addEventListener("navigated", schedule);
    document.addEventListener("livewire:navigated", schedule);
    document.addEventListener("kona:navigated", schedule);
    document.addEventListener("click", function () {
      document.querySelectorAll("[data-ff-nav-more]").forEach(sync);
    });
    document.addEventListener("keydown", onKey);
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
