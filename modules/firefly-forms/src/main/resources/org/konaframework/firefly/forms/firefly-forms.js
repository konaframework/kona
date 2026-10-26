// Firefly forms. Register on the Alpine instance Wire already started.
// This file does not start Alpine. Wire already did.
// Rich text uses TipTap (grouped sticky toolbar, bubble menu, link and image Dialog).
// Code uses CodeMirror 6 plus Shiki (full language catalog). Not Monaco. Not Prism.
// Markdown write uses CodeMirror @codemirror/lang-markdown. Preview uses markdown-it GFM, sanitized into .ff-prose.
// Uploads use FilePond, including the image editor plugin, posting to POST /_firefly/uploads.

export function registerFireflyForms(Alpine) {
  Alpine.data("ffCombobox", (path) => editor(path));
  Alpine.data("ffDatePicker", (path) => datePicker(path));
  Alpine.data("ffFilePond", (path) => filePond(path));
  Alpine.data("ffRichEditor", (path) => richEditor(path));
  Alpine.data("ffCodeEditor", (path) => codeEditor(path));
  Alpine.data("ffMarkdown", (path) => markdownEditor(path));
  Alpine.data("ffTags", (path) => editor(path));
  Alpine.data("ffKeyValue", (path) => editor(path));
  Alpine.data("ffSlider", (path) => editor(path));
  Alpine.data("ffColor", (path) => editor(path));
  Alpine.data("ffRepeater", (path) => editor(path));
  Alpine.data("ffBuilder", (path) => editor(path));
}

function editor(path) {
  return {
    path,
    init() {
      const key = this.path || this.$el.dataset.ffPath;
      if (this.$wire && key) this.$wire.$entangle(key, true);
    },
  };
}

function datePicker(path) {
  const base = editor(path);
  return {
    ...base,
    shift(days) {
      const state = this.$el.dataset.ffState;
      const focused = state ? state.slice(0, 10) : "";
      this.$el.dataset.ffFocused = focused;
      return days;
    },
  };
}

function filePond(path) {
  const base = editor(path);
  return {
    ...base,
    init() {
      base.init.call(this);
      const input = this.$el.querySelector("input[type=file]");
      if (!input) return;
      const options = pondOptions(this.$el);
      if (globalThis.FilePond) {
        const plugins = [];
        if (globalThis.FilePondPluginFileValidateType) plugins.push(globalThis.FilePondPluginFileValidateType);
        if (globalThis.FilePondPluginFileValidateSize) plugins.push(globalThis.FilePondPluginFileValidateSize);
        if (globalThis.FilePondPluginImagePreview) plugins.push(globalThis.FilePondPluginImagePreview);
        if (globalThis.FilePondPluginImageExifOrientation) plugins.push(globalThis.FilePondPluginImageExifOrientation);
        if (this.$el.dataset.ffImageEditor === "true" && globalThis.FilePondPluginImageEdit) {
          plugins.push(globalThis.FilePondPluginImageEdit);
        }
        if (plugins.length) globalThis.FilePond.registerPlugin(...plugins);
        globalThis.FilePond.create(input, options);
        return;
      }
      input.addEventListener("change", () => postFiles(input, this.$el.dataset.ffUpload, this.$el.dataset.ffDisk));
    },
  };
}

function pondOptions(el) {
  return {
    allowMultiple: el.dataset.ffMultiple === "true",
    allowReorder: el.dataset.ffReorder === "true",
    allowPaste: el.dataset.ffPaste === "true",
    acceptedFileTypes: (el.dataset.ffAccept || "").split(",").filter(Boolean),
    server: {
      process: {
        url: el.dataset.ffUpload || "/_firefly/uploads",
        method: "POST",
        headers: csrfHeaders(),
      },
    },
    imageEditAllowEdit: el.dataset.ffImageEditor === "true",
  };
}

function richEditor(path) {
  const base = editor(path);
  return {
    ...base,
    init() {
      base.init.call(this);
      const mount = this.$el.querySelector(".ff-prose");
      if (globalThis.FireflyEditors && mount) {
        globalThis.FireflyEditors.richText(mount, this.$el);
      }
    },
  };
}

function codeEditor(path) {
  const base = editor(path);
  return {
    ...base,
    init() {
      base.init.call(this);
      const mount = this.$el.querySelector("textarea");
      if (globalThis.FireflyEditors && mount) {
        globalThis.FireflyEditors.code(mount, {
          language: this.$el.dataset.ffLanguage || "plaintext",
          wrap: this.$el.dataset.ffWrap === "true",
          highlighter: "shiki",
        });
      }
    },
  };
}

function markdownEditor(path) {
  const base = editor(path);
  return {
    ...base,
    mode: "write",
    init() {
      base.init.call(this);
      const mount = this.$el.querySelector("[data-ff-write]");
      if (globalThis.FireflyEditors && mount) {
        globalThis.FireflyEditors.markdown(mount, this.$el);
      }
    },
    preview() {
      this.mode = "preview";
      const source = this.$el.querySelector("[data-ff-write]");
      const prose = this.$el.querySelector(".ff-prose");
      if (globalThis.FireflyEditors && source && prose) {
        prose.innerHTML = globalThis.FireflyEditors.markdownPreview(source.value);
      }
    },
  };
}

function csrfHeaders() {
  const meta = document.querySelector('meta[name="csrf-token"]');
  if (!meta) return {};
  return { "X-CSRF-TOKEN": meta.getAttribute("content") || "" };
}

function postFiles(input, url, disk) {
  const body = new FormData();
  body.append("disk", disk || "local");
  const token = csrfHeaders()["X-CSRF-TOKEN"];
  if (token) body.append("_token", token);
  for (const file of input.files || []) body.append("file", file);
  fetch(url || "/_firefly/uploads", { method: "POST", body, headers: csrfHeaders() });
}
