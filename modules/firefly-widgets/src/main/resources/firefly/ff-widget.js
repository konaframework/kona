export function registerFireflyWidgets(Alpine) {
  Alpine.data("ffWidget", (config) => Object.assign({}, config || {}));
}
