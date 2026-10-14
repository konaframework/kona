/**
 * Crema extension stub for VS Code / Cursor.
 *
 * Language id + grammar come from package.json. This activate hook only
 * registers a command that prints how to run kona-lsp — it does not keep a
 * language server process alive (pair `crema.lsp.jarPath` with an LSP client
 * such as vscode-languageclient when you want full diagnostics/completion).
 *
 * `vscode` is provided by the host at runtime.
 */
function activate(context) {
  let vscode;
  try {
    vscode = require('vscode');
  } catch {
    return;
  }

  const showLspHelp = () => {
    const cfg = vscode.workspace.getConfiguration('crema');
    const jar = cfg.get('lsp.jarPath') || '<path-to-kona-lsp.jar>';
    const java = cfg.get('lsp.javaPath') || 'java';
    const lines = [
      'kona-lsp (Crema language server)',
      '',
      'Build:  ./gradlew :kona-lsp:jar',
      'Run:    ' + java + ' -jar ' + jar,
      '',
      'Point your editor LSP client at that command (stdio).',
      'Settings: crema.lsp.javaPath, crema.lsp.jarPath',
      'Also: ./grind ide:lsp',
    ];
    vscode.window.showInformationMessage(lines[0] + ' — see Output / docs');
    const channel = vscode.window.createOutputChannel('Crema');
    channel.appendLine(lines.join('\n'));
    channel.show(true);
  };

  context.subscriptions.push(
    vscode.commands.registerCommand('crema.lsp.showHelp', showLspHelp),
  );
}

function deactivate() {}

module.exports = { activate, deactivate };
