(function () {
  function registerFireflyWidgets(Alpine) {
    Alpine.data("ffWidget", function (config) {
      return Object.assign({}, config || {});
    });
  }

  window.registerFireflyWidgets = registerFireflyWidgets;

  /**
   * Lazy-load + polling island for dashboard widgets (Shamar/Filament parity).
   * Expects data-ff-refresh-url, optional data-ff-polling-interval (ms), data-ff-lazy.
   */
  window.ffDashboardWidget = function ffDashboardWidget(el) {
    return {
      timer: null,
      observer: null,
      loaded: false,
      async init() {
        const node = el || this.$el;
        const lazy = node?.dataset?.ffLazy === "1";
        if (lazy && "IntersectionObserver" in window) {
          this.observer = new IntersectionObserver(
            (entries) => {
              if (entries.some((entry) => entry.isIntersecting)) {
                this.observer?.disconnect();
                this.observer = null;
                this.load();
              }
            },
            { rootMargin: "80px" },
          );
          this.observer.observe(node);
        } else if (lazy) {
          await this.load();
        } else {
          this.loaded = true;
          this.startPolling();
        }
      },
      async load() {
        const node = el || this.$el;
        const url = node?.dataset?.ffRefreshUrl;
        if (!url) return;
        try {
          const query = window.location.search || "";
          const sep = url.includes("?") ? "&" : "?";
          const res = await fetch(`${url}${query ? sep + query.slice(1) : ""}`, {
            headers: { Accept: "text/html", "X-Requested-With": "XMLHttpRequest" },
            credentials: "same-origin",
          });
          if (!res.ok) throw new Error("HTTP " + res.status);
          const html = await res.text();
          const template = document.createElement("template");
          template.innerHTML = html.trim();
          const next = template.content.firstElementChild;
          if (!next) return;
          const parent = node.closest(".ff-component.ff-widget") || node;
          parent.replaceWith(next);
          if (window.Alpine?.initTree) window.Alpine.initTree(next);
          this.loaded = true;
        } catch (error) {
          console.error("[firefly] dashboard widget refresh failed", error);
          const body = node.querySelector("[data-ff-widget-body]");
          if (body) {
            body.innerHTML = '<p class="ff-widget-empty">Failed to load widget.</p>';
          }
        }
      },
      startPolling() {
        const node = el || this.$el;
        const ms = Number(node?.dataset?.ffPollingInterval || 0);
        if (!ms || ms < 250) return;
        this.timer = window.setInterval(() => this.load(), ms);
      },
      destroy() {
        if (this.timer) window.clearInterval(this.timer);
        this.observer?.disconnect();
      },
    };
  };

  document.addEventListener("alpine:init", function () {
    if (window.Alpine) registerFireflyWidgets(window.Alpine);
  });
})();
