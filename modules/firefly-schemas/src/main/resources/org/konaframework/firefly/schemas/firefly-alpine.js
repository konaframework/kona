export function registerFirefly(Alpine) {
  const entangle = (path, live) => ({
    path,
    live,
    init() {
      this.$wire.$entangle(path, live);
    },
  });
  Alpine.data("ffField", entangle);
  Alpine.data("ffEntry", entangle);
  Alpine.data("ffColumn", entangle);
  Alpine.data("ffAction", (name) => ({
    name,
    run() {
      this.$wire.$call(name);
    },
  }));
}
