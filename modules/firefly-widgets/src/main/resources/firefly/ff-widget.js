(function () {
  function registerFireflyWidgets(Alpine) {
    Alpine.data("ffWidget", function (config) {
      return Object.assign({}, config || {});
    });
  }

  window.registerFireflyWidgets = registerFireflyWidgets;

  document.addEventListener("alpine:init", function () {
    if (window.Alpine) registerFireflyWidgets(window.Alpine);
  });
})();
