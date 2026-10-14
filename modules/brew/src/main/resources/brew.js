(function () {
  "use strict";

  function brewRoot(el) {
    return el.closest("[data-brew]");
  }

  function attr(el, names) {
    for (var i = 0; i < names.length; i++) {
      var v = el.getAttribute(names[i]);
      if (v != null && v !== "") return v;
    }
    return null;
  }

  function collectModels(root) {
    var params = {};
    var nodes = root.querySelectorAll("[wire\\:model], [data-brew-model]");
    for (var i = 0; i < nodes.length; i++) {
      var el = nodes[i];
      var prop = attr(el, ["wire:model", "data-brew-model"]);
      if (!prop) continue;
      if (el.type === "checkbox") {
        params[prop] = el.checked;
      } else {
        params[prop] = el.value;
      }
    }
    return params;
  }

  function morphReplace(root, html) {
    var focusProp = null;
    var active = document.activeElement;
    if (active && root.contains(active)) {
      focusProp = attr(active, ["wire:model", "data-brew-model"]);
    }
    var tmp = document.createElement("div");
    tmp.innerHTML = html;
    var next = tmp.firstElementChild;
    if (!next) return;
    root.replaceWith(next);
    if (focusProp) {
      var again = next.querySelector(
        '[wire\\:model="' + focusProp + '"], [data-brew-model="' + focusProp + '"]'
      );
      if (again && typeof again.focus === "function") {
        again.focus();
      }
    }
  }

  function send(root, method, params) {
    var body = {
      name: root.getAttribute("data-brew-name") || "",
      snapshot: root.getAttribute("data-brew") || "",
      id: root.id || "",
      method: method,
      params: params || {},
    };
    return fetch("/brew/message", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
      },
      body: JSON.stringify(body),
    })
      .then(function (res) {
        return res.json().then(function (data) {
          if (!res.ok) {
            throw new Error((data && data.error) || "Brew update failed");
          }
          return data;
        });
      })
      .then(function (data) {
        if (data && data.html) {
          morphReplace(root, data.html);
        }
      });
  }

  document.addEventListener(
    "click",
    function (e) {
      var t = e.target.closest("[wire\\:click], [data-brew-click]");
      if (!t) return;
      var root = brewRoot(t);
      if (!root) return;
      e.preventDefault();
      var method = attr(t, ["wire:click", "data-brew-click"]);
      if (!method) return;
      send(root, method, collectModels(root));
    },
    true
  );

  document.addEventListener(
    "submit",
    function (e) {
      var form = e.target.closest("[wire\\:submit], [data-brew-submit]");
      if (!form) return;
      var root = brewRoot(form);
      if (!root) return;
      e.preventDefault();
      var method = attr(form, ["wire:submit", "data-brew-submit"]);
      if (!method) return;
      send(root, method, collectModels(root));
    },
    true
  );

  document.addEventListener(
    "change",
    function (e) {
      var t = e.target.closest("[wire\\:model], [data-brew-model]");
      if (!t) return;
      var root = brewRoot(t);
      if (!root) return;
      var prop = attr(t, ["wire:model", "data-brew-model"]);
      if (!prop) return;
      var params = {};
      params[prop] = t.type === "checkbox" ? t.checked : t.value;
      send(root, "sync", params);
    },
    true
  );
})();
