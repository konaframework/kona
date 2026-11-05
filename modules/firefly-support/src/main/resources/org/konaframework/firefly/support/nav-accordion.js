/**
 * SIDE nav exclusive accordion: when a collapsible group opens, close siblings.
 * Opt-in via <nav class="ff-nav-side" data-ff-nav-accordion> (Panel.navigationAccordion).
 *
 * Binds `toggle` on each <details> (toggle does not bubble) and re-arms after soft
 * navigations / sidebar morphs so exclusivity works on live click, not only reload.
 */
(function () {
  var ATTR = "data-ff-nav-accordion-armed";
  var armTimer = 0;

  function panelRoot() {
    return document.querySelector(".ff-panel");
  }

  function collapseSiblings(openDetails) {
    var nav = openDetails.closest("nav[data-ff-nav-accordion]");
    if (!nav) return;
    nav.querySelectorAll("details.ff-nav-group-collapsible").forEach(function (other) {
      if (other !== openDetails && other.open) other.open = false;
    });
  }

  function onToggle(ev) {
    var d = ev.currentTarget || ev.target;
    if (!d || !d.open) return;
    collapseSiblings(d);
  }

  /**
   * Click on <summary> fires before the open state flips. If the group is about to open,
   * close siblings immediately so two groups never paint open together.
   */
  function onSummaryClick(ev) {
    var t = ev.target;
    if (!t || !t.closest) return;
    var summary = t.closest("summary");
    if (!summary) return;
    var details = summary.parentElement;
    if (!details || !details.matches || !details.matches("details.ff-nav-group-collapsible")) return;
    if (!details.closest("nav[data-ff-nav-accordion]")) return;
    if (details.open) return;
    collapseSiblings(details);
  }

  function normalizeOpenGroups(nav) {
    var opens = nav.querySelectorAll("details.ff-nav-group-collapsible[open]");
    if (opens.length <= 1) return;
    var keep = null;
    opens.forEach(function (d) {
      if (d.querySelector(".ff-active, a.ff-active, .ff-nav-item.ff-active")) keep = d;
    });
    if (!keep) keep = opens[0];
    opens.forEach(function (d) {
      if (d !== keep) d.open = false;
    });
  }

  function arm() {
    var root = panelRoot();
    if (!root) return;
    root.querySelectorAll("nav[data-ff-nav-accordion]").forEach(function (nav) {
      normalizeOpenGroups(nav);
      nav.querySelectorAll("details.ff-nav-group-collapsible").forEach(function (d) {
        if (d.getAttribute(ATTR) === "true") return;
        d.setAttribute(ATTR, "true");
        d.addEventListener("toggle", onToggle);
      });
    });
  }

  function scheduleArm() {
    if (armTimer) return;
    armTimer = setTimeout(function () {
      armTimer = 0;
      arm();
    }, 0);
  }

  function boot() {
    arm();
    document.addEventListener("click", onSummaryClick, true);
    document.addEventListener("navigated", scheduleArm);
    document.addEventListener("livewire:navigated", scheduleArm);
    document.addEventListener("kona:navigated", scheduleArm);
    if (typeof MutationObserver === "function") {
      var obs = new MutationObserver(scheduleArm);
      obs.observe(document.documentElement, { childList: true, subtree: true });
    }
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", boot);
  } else {
    boot();
  }
})();
