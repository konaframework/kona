// Firefly code entry. The browser highlights with Shiki createHighlighter.
// Grammars and themes are the same set a CodeMirror editor loads.
// The server sends the raw source and the grammar name on data-ff-grammar.
export async function highlightCodeEntry(root, shiki) {
  if (!root || !shiki || !shiki.createHighlighter) return;
  const nodes = root.querySelectorAll("[data-ff-shiki='shiki']");
  const themes = new Set();
  const langs = new Set();
  nodes.forEach((node) => {
    themes.add(node.getAttribute("data-ff-light-theme") || "github-light");
    themes.add(node.getAttribute("data-ff-dark-theme") || "github-dark");
    langs.add(node.getAttribute("data-ff-grammar") || "html");
  });
  if (themes.size === 0) return;
  const highlighter = await shiki.createHighlighter({
    themes: [...themes],
    langs: [...langs],
  });
  nodes.forEach((node) => {
    const code = node.querySelector("code");
    if (!code) return;
    const lang = node.getAttribute("data-ff-grammar") || "html";
    const light = node.getAttribute("data-ff-light-theme") || "github-light";
    const dark = node.getAttribute("data-ff-dark-theme") || "github-dark";
    node.querySelector("pre").innerHTML = highlighter.codeToHtml(code.textContent, {
      lang,
      themes: { light, dark },
    });
  });
}
