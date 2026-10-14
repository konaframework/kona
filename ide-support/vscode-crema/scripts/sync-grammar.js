const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..', '..', 'crema');
const syntaxes = path.join(__dirname, '..', 'syntaxes');
fs.mkdirSync(syntaxes, { recursive: true });
fs.copyFileSync(
  path.join(root, 'crema.tmLanguage.json'),
  path.join(syntaxes, 'crema.tmLanguage.json'),
);
fs.copyFileSync(
  path.join(root, 'language-configuration.json'),
  path.join(__dirname, '..', 'language-configuration.json'),
);
console.log('Synced Crema grammar into vscode-crema');
