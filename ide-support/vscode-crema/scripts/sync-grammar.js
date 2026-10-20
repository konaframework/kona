const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..', '..', 'crema');
const syntaxes = path.join(__dirname, '..', 'syntaxes');
fs.mkdirSync(syntaxes, { recursive: true });
const files = [
  'crema.tmLanguage.json',
  'crema.injection.tmLanguage.json',
  'crema.string-tokens.tmLanguage.json',
];
for (const name of files) {
  fs.copyFileSync(path.join(root, name), path.join(syntaxes, name));
}
fs.copyFileSync(
  path.join(root, 'language-configuration.json'),
  path.join(__dirname, '..', 'language-configuration.json'),
);
console.log('Synced Crema grammar into vscode-crema');
