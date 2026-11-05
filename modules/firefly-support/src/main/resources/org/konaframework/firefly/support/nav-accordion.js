/**
 * SIDE nav exclusive accordion: when a collapsible group opens, close siblings.
 * Opt-in via <nav class="ff-nav-side" data-ff-nav-accordion> (Panel.navigationAccordion).
 */
(function () {
  var root = document.querySelector(".ff-panel");
  if (!root) return;

  function collapseSiblings(openDetails) {
    var nav = openDetails.closest("nav[data-ff-nav-accordion]");
    if (!nav) return;
    nav.querySelectorAll("details.ff-nav-group-collapsible").forEach(function (other) {
      if (other !== openDetails && other.open) other.open = false;
    });
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
    root.querySelectorAll("nav[data-ff-nav-accordion]").forEach(normalizeOpenGroups);
  }

  arm();
  document.addEventListener(
    "toggle",
    function (ev) {
      var d = ev.target;
      if (!d || !d.matches || !d.matches("details.ff-nav-group-collapsible")) return;
      if (!d.open) return;
      collapseSiblings(d);
    },
    true,
  );
  document.addEventListener("navigated", arm);
  document.addEventListener("livewire:navigated", arm);
})();
