/**
 * In-panel navigation for Panel.spa(true).
 * Clicks fetch the next document and swap the shell. They do not assign location.
 */
(function () {
  if (window.__ffSpa) return;
  window.__ffSpa = true;

  var ticket = 0;

  function absolute(url) {
    return new URL(url, window.location.href).href;
  }

  function sameOrigin(url) {
    try {
      return new URL(url, window.location.href).origin === window.location.origin;
    } catch (e) {
      return false;
    }
  }

  function ignored(link) {
    if (!link || link.hasAttribute("download")) return true;
    if (link.closest("a[data-ff-table-nav], [data-ff-relation-tab]")) return true;
    var target = (link.getAttribute("target") || "").toLowerCase();
    if (target && target !== "_self") return true;
    var href = (link.getAttribute("href") || "").trim();
    if (!href || href.charAt(0) === "#") return true;
    var low = href.toLowerCase();
    if (low.indexOf("mailto:") === 0 || low.indexOf("javascript:") === 0 || low.indexOf("tel:") === 0) return true;
    return !sameOrigin(href);
  }

  function navLink(target) {
    if (!target || !target.closest) return null;
    var link = target.closest("a[href]");
    if (!link || !link.closest(".ff-panel")) return null;
    if (ignored(link)) return null;
    if (link.hasAttribute("wire:navigate")) return link;
    if (link.closest(".ff-nav, .ff-breadcrumbs, .ff-user, .ff-tenants, .ff-tenant-menu, .ff-page-header, .ff-page-footer")) {
      return link;
    }
    return null;
  }

  function notHtml(response) {
    var disposition = (response.headers.get("Content-Disposition") || "").toLowerCase();
    if (disposition.indexOf("attachment") !== -1) return true;
    var type = (response.headers.get("Content-Type") || "").toLowerCase();
    if (!type) return false;
    return type.indexOf("text/html") === -1 && type.indexOf("application/xhtml+xml") === -1;
  }

  function fetchFile(url) {
    var frame = document.createElement("iframe");
    frame.hidden = true;
    frame.setAttribute("data-ff-panel-spa-file", "");
    frame.src = url;
    document.body.appendChild(frame);
  }

  function swapRegions(doc) {
    var swapped = false;
    [".ff-sidebar", ".ff-topbar", ".ff-scroll"].forEach(function (sel) {
      var cur = document.querySelector(sel);
      var next = doc.querySelector(sel);
      if (!cur || !next) return;
      cur.innerHTML = next.innerHTML;
      swapped = true;
    });
    var curPanel = document.querySelector(".ff-panel");
    var nextPanel = doc.querySelector(".ff-panel");
    if (curPanel && nextPanel) {
      var width = nextPanel.getAttribute("data-ff-max-width");
      if (width) curPanel.setAttribute("data-ff-max-width", width);
      else curPanel.removeAttribute("data-ff-max-width");
      var kept = [];
      curPanel.className.split(/\s+/).forEach(function (name) {
        if (name && name !== "container" && name.indexOf("max-w-") !== 0) kept.push(name);
      });
      nextPanel.className.split(/\s+/).forEach(function (name) {
        if (name === "container" || name.indexOf("max-w-") === 0) kept.push(name);
      });
      curPanel.className = kept.join(" ");
      var arbitrary = nextPanel.style.getPropertyValue("--ff-arbitrary-max");
      if (arbitrary) curPanel.style.setProperty("--ff-arbitrary-max", arbitrary);
      else curPanel.style.removeProperty("--ff-arbitrary-max");
    }
    return swapped;
  }

  function remember(url, replace) {
    try {
      var state = { ffSpa: 1 };
      if (replace) history.replaceState(state, "", url);
      else history.pushState(state, "", url);
    } catch (e) {}
    var detail = { url: url };
    document.dispatchEvent(new CustomEvent("kona:navigated", { bubbles: true, detail: detail }));
    document.dispatchEvent(new CustomEvent("livewire:navigated", { bubbles: true, detail: detail }));
    document.dispatchEvent(new CustomEvent("wire:navigated", { bubbles: true, detail: detail }));
  }

  function show(html, url, replace) {
    var doc = new DOMParser().parseFromString(html, "text/html");
    if (doc.title) document.title = doc.title;
    if (swapRegions(doc)) {
      var scroll = document.querySelector(".ff-scroll");
      if (scroll) scroll.scrollTop = 0;
      remember(url, replace);
      return;
    }
    document.open();
    document.write(html);
    document.close();
    try {
      history.replaceState({ ffSpa: 1 }, "", url);
    } catch (e) {}
  }

  function navigate(url, replace) {
    var target;
    try {
      target = absolute(url);
    } catch (e) {
      return;
    }
    if (!sameOrigin(target)) return;
    var mine = ++ticket;
    fetch(target, {
      credentials: "same-origin",
      headers: { "X-Brew-Navigate": "1", Accept: "text/html" },
    })
      .then(function (response) {
        if (notHtml(response)) {
          fetchFile(response.url || target);
          return null;
        }
        return response.text().then(function (html) {
          return { html: html, url: response.url || target };
        });
      })
      .then(function (page) {
        if (!page || mine !== ticket) return;
        show(page.html, page.url, !!replace);
      })
      .catch(function () {});
  }

  window.ffSpaNavigate = function (url, replace) {
    navigate(url, !!replace);
  };

  document.addEventListener(
    "click",
    function (ev) {
      if (ev.defaultPrevented || ev.metaKey || ev.ctrlKey || ev.shiftKey || ev.altKey || ev.button) return;
      var node = ev.target;
      if (!node || !node.closest) return;
      var link = navLink(node);
      if (!link) return;
      ev.preventDefault();
      navigate(link.getAttribute("href"), false);
    },
    true,
  );

  window.addEventListener("popstate", function () {
    navigate(window.location.href, true);
  });
})();
